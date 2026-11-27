package de.davis.keygo.core.sync.domain.stepup

import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Single(binds = [StepUpGate::class, StepUpPrompter::class])
internal class StepUpGateImpl(
    private val syncRepository: SyncRepository,
) : StepUpGate, StepUpPrompter {

    private val promptLock = Mutex()

    /** Lets a caller that waited on [promptLock] see that a step-up happened meanwhile. */
    private val stepUps = AtomicLong()

    /** Cancelled by [abandon]; each run captures the one current when it starts. */
    private val generation = AtomicReference(Job())

    @Volatile
    private var answer = CompletableDeferred<String?>(null)

    private val _prompt = MutableStateFlow<StepUpPrompt?>(null)
    override val prompt: StateFlow<StepUpPrompt?> = _prompt.asStateFlow()

    override suspend fun <T> run(
        force: Boolean,
        call: suspend () -> Result<T, SyncError>,
    ): Result<T, SyncError> {
        suspend fun attempt() = call().takeUnless { it.needsFreshAuth() }
        val seen = stepUps.get()
        val gen = generation.get()

        if (!force) attempt()?.let { return it }

        promptLock.withLock {
            if (gen.isCancelled) return Result.Failure(SyncError.StepUpCancelled)
            // Another caller stepped up while this one waited for the lock: retry before prompting,
            // so concurrent callers share one prompt.
            if (!force && stepUps.get() != seen) attempt()?.let { return it }

            abandonable(gen) { stepUp() }?.let { return Result.Failure(it) }
            stepUps.incrementAndGet()
        }

        return call()
    }

    override fun submit(password: String) {
        answer.complete(password)
    }

    override fun cancel() {
        answer.complete(null)
    }

    override fun abandon() {
        generation.getAndSet(Job()).cancel()
        _prompt.update { null }
    }

    private suspend fun abandonable(gen: Job, block: suspend () -> SyncError?): SyncError? =
        coroutineScope {
            val work = async { block() }
            val handle = gen.invokeOnCompletion { work.cancel() }

            try {
                work.await()
            } catch (_: CancellationException) {
                // If the caller itself was cancelled, coroutineScope rethrows despite this value.
                SyncError.StepUpCancelled
            } finally {
                handle.dispose()
            }
        }

    private suspend fun stepUp(): SyncError? {
        var error: SyncError? = null
        try {
            while (true) {
                answer = CompletableDeferred()
                _prompt.update { StepUpPrompt(error = error) }

                val password = answer.await() ?: return SyncError.StepUpCancelled

                _prompt.update { StepUpPrompt(busy = true) }
                when (val result = syncRepository.stepUp(password)) {
                    is Result.Success -> return null
                    is Result.Failure -> if (result.error.isRetryable()) error = result.error
                    else return result.error
                }
            }
        } finally {
            _prompt.update { null }
        }
    }

    private fun SyncError.isRetryable() =
        this == SyncError.InvalidCredentials ||
                this == SyncError.RateLimited ||
                this is SyncError.Network

    private fun Result<*, SyncError>.needsFreshAuth() =
        this is Result.Failure && error == SyncError.FreshAuthRequired
}

package de.davis.keygo.core.sync.domain.stepup

import de.davis.keygo.core.sync.FakeSyncRepository
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StepUpGateImplTest {

    private val syncRepository = FakeSyncRepository()
    private val gate = StepUpGateImpl(syncRepository)

    private class Call(vararg results: Result<String, SyncError>) {
        private val answers = results.toList()
        var runs = 0
            private set

        operator fun invoke(): Result<String, SyncError> = answers.getOrElse(runs++) {
            answers.last()
        }
    }

    private fun runTestUnconfined(block: suspend TestScope.() -> Unit) =
        runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun `a successful call passes through without a prompt`() = runTestUnconfined {
        val call = Call(Result.Success("ok"))

        val result = gate.run { call() }

        assertEquals(Result.Success("ok"), result)
        assertEquals(1, call.runs)
        assertNull(gate.prompt.value)
        assertEquals(emptyList(), syncRepository.stepUpPasswords)
    }

    @Test
    fun `a failure other than fresh auth passes through without a prompt`() = runTestUnconfined {
        val failure = Result.Failure<String, SyncError>(SyncError.Network(NetworkFailure.Timeout))
        val call = Call(failure)

        val result = gate.run { call() }

        assertEquals(failure, result)
        assertEquals(1, call.runs)
        assertNull(gate.prompt.value)
    }

    @Test
    fun `fresh auth prompts, steps up with the password and runs the call again`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))

            val result = async { gate.run { call() } }

            assertEquals(StepUpPrompt(), gate.prompt.value)
            assertEquals(1, call.runs)

            gate.submit("hunter2")

            assertEquals(Result.Success("done"), result.await())
            assertEquals(listOf("hunter2"), syncRepository.stepUpPasswords)
            assertEquals(2, call.runs)
            assertNull(gate.prompt.value)
        }

    @Test
    fun `the prompt is busy while the step-up runs`() = runTestUnconfined {
        val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
        syncRepository.stepUpGate = CompletableDeferred()

        val result = async { gate.run { call() } }
        gate.submit("hunter2")

        assertEquals(StepUpPrompt(busy = true), gate.prompt.value)

        gate.submit("again")
        gate.cancel()
        syncRepository.stepUpGate?.complete(Unit)

        assertEquals(Result.Success("done"), result.await())
        assertEquals(listOf("hunter2"), syncRepository.stepUpPasswords)
    }

    @Test
    fun `a wrong password shows the error and accepts another try`() = runTestUnconfined {
        val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
        syncRepository.stepUpResults = listOf(
            Result.Failure(SyncError.InvalidCredentials),
            Result.Success(Unit),
        )

        val result = async { gate.run { call() } }
        gate.submit("wrong")

        assertEquals(StepUpPrompt(error = SyncError.InvalidCredentials), gate.prompt.value)
        assertFalse(result.isCompleted)

        gate.submit("right")

        assertEquals(Result.Success("done"), result.await())
        assertEquals(listOf("wrong", "right"), syncRepository.stepUpPasswords)
        assertEquals(2, call.runs)
        assertNull(gate.prompt.value)
    }

    @Test
    fun `cancelling the prompt returns StepUpCancelled without running the call again`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))

            val result = async { gate.run { call() } }
            gate.cancel()

            assertEquals(Result.Failure(SyncError.StepUpCancelled), result.await())
            assertEquals(1, call.runs)
            assertNull(gate.prompt.value)
            assertEquals(emptyList(), syncRepository.stepUpPasswords)
        }

    @Test
    fun `fresh auth again after a step-up is returned instead of prompting again`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired))

            val result = async { gate.run { call() } }
            gate.submit("hunter2")

            assertEquals(Result.Failure(SyncError.FreshAuthRequired), result.await())
            assertEquals(2, call.runs)
            assertEquals(1, syncRepository.stepUpPasswords.size)
            assertNull(gate.prompt.value)
        }

    @Test
    fun `force prompts before the first call`() = runTestUnconfined {
        val call = Call(Result.Success("done"))

        val result = async { gate.run(force = true) { call() } }

        assertEquals(StepUpPrompt(), gate.prompt.value)
        assertEquals(0, call.runs)

        gate.submit("hunter2")

        assertEquals(Result.Success("done"), result.await())
        assertEquals(1, call.runs)
    }

    @Test
    fun `two callers needing fresh auth share one prompt`() = runTestUnconfined {
        val first = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("first"))
        val second = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("second"))

        val a = async { gate.run { first() } }
        val b = async { gate.run { second() } }

        assertEquals(StepUpPrompt(), gate.prompt.value)

        gate.submit("hunter2")

        assertEquals(Result.Success("first"), a.await())
        assertEquals(Result.Success("second"), b.await())
        assertEquals(listOf("hunter2"), syncRepository.stepUpPasswords)
        assertEquals(2, first.runs)
        assertEquals(2, second.runs)
        assertNull(gate.prompt.value)
    }

    @Test
    fun `a waiting caller is prompted when the other caller cancelled`() = runTestUnconfined {
        val first = Call(Result.Failure(SyncError.FreshAuthRequired))
        val second = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("second"))

        val a = async { gate.run { first() } }
        val b = async { gate.run { second() } }
        gate.cancel()

        assertEquals(Result.Failure(SyncError.StepUpCancelled), a.await())
        assertEquals(StepUpPrompt(), gate.prompt.value)

        gate.submit("hunter2")

        assertEquals(Result.Success("second"), b.await())
        assertEquals(2, second.runs)
    }

    @Test
    fun `a waiting caller still needing fresh auth after the shared step-up is prompted again`() =
        runTestUnconfined {
            val first = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("first"))
            val second = Call(
                Result.Failure(SyncError.FreshAuthRequired),
                Result.Failure(SyncError.FreshAuthRequired),
                Result.Success("second"),
            )

            val a = async { gate.run { first() } }
            val b = async { gate.run { second() } }
            gate.submit("one")

            assertEquals(Result.Success("first"), a.await())
            assertEquals(StepUpPrompt(), gate.prompt.value)
            assertEquals(2, second.runs)

            gate.submit("two")

            assertEquals(Result.Success("second"), b.await())
            assertEquals(3, second.runs)
            assertEquals(listOf("one", "two"), syncRepository.stepUpPasswords)
        }

    @Test
    fun `a forced caller waiting for the prompt does not share another caller's step-up`() =
        runTestUnconfined {
            val first = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("first"))
            val forced = Call(Result.Success("forced"))

            val a = async { gate.run { first() } }
            val b = async { gate.run(force = true) { forced() } }
            gate.submit("one")

            assertEquals(Result.Success("first"), a.await())
            assertEquals(StepUpPrompt(), gate.prompt.value)
            assertEquals(0, forced.runs)

            gate.submit("two")

            assertEquals(Result.Success("forced"), b.await())
            assertEquals(1, forced.runs)
            assertEquals(listOf("one", "two"), syncRepository.stepUpPasswords)
        }

    @Test
    fun `cancelling the caller clears the prompt`() = runTestUnconfined {
        val call = Call(Result.Failure(SyncError.FreshAuthRequired))

        val result = async { gate.run { call() } }
        assertEquals(StepUpPrompt(), gate.prompt.value)

        result.cancel()
        testScheduler.runCurrent()

        assertNull(gate.prompt.value)
        gate.submit("late")
        assertEquals(emptyList(), syncRepository.stepUpPasswords)
    }

    @Test
    fun `cancelling the caller during a step-up cancels the run instead of returning StepUpCancelled`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
            syncRepository.stepUpGate = CompletableDeferred()
            var returned: Result<String, SyncError>? = null

            val result = async { returned = gate.run { call() } }
            gate.submit("hunter2")
            assertEquals(StepUpPrompt(busy = true), gate.prompt.value)

            result.cancel()
            testScheduler.runCurrent()

            assertTrue(result.isCancelled)
            assertNull(returned)
            assertNull(gate.prompt.value)
            assertEquals(1, call.runs)
        }

    @Test
    fun `abandoning a waiting prompt returns StepUpCancelled without running the call again`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))

            val result = async { gate.run { call() } }
            assertEquals(StepUpPrompt(), gate.prompt.value)

            gate.abandon()

            assertNull(gate.prompt.value)
            assertEquals(Result.Failure(SyncError.StepUpCancelled), result.await())
            assertEquals(1, call.runs)
            assertEquals(emptyList(), syncRepository.stepUpPasswords)
        }

    @Test
    fun `abandoning during a step-up discards its result and does not run the call again`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
            syncRepository.stepUpGate = CompletableDeferred()

            val result = async { gate.run { call() } }
            gate.submit("hunter2")
            assertEquals(StepUpPrompt(busy = true), gate.prompt.value)

            gate.abandon()

            assertNull(gate.prompt.value)
            syncRepository.stepUpGate?.complete(Unit)

            assertEquals(Result.Failure(SyncError.StepUpCancelled), result.await())
            assertEquals(1, call.runs)
            assertNull(gate.prompt.value)
        }

    @Test
    fun `abandoning also ends callers waiting for the prompt`() = runTestUnconfined {
        val first = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("first"))
        val second = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("second"))

        val a = async { gate.run { first() } }
        val b = async { gate.run { second() } }

        gate.abandon()

        assertEquals(Result.Failure(SyncError.StepUpCancelled), a.await())
        assertEquals(Result.Failure(SyncError.StepUpCancelled), b.await())
        assertNull(gate.prompt.value)
        assertEquals(1, first.runs)
        assertEquals(1, second.runs)
    }

    @Test
    fun `abandoning while the first call runs returns StepUpCancelled without prompting`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
            val firstCall = CompletableDeferred<Unit>()

            val result = async {
                gate.run {
                    if (call.runs == 0) firstCall.await()
                    call()
                }
            }
            gate.abandon()
            firstCall.complete(Unit)

            assertTrue(result.isCompleted)
            assertEquals(Result.Failure(SyncError.StepUpCancelled), result.await())
            assertNull(gate.prompt.value)
            assertEquals(1, call.runs)
            assertEquals(emptyList(), syncRepository.stepUpPasswords)
        }

    @Test
    fun `a step-up after an abandon prompts again`() = runTestUnconfined {
        gate.abandon()

        val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
        val result = async { gate.run { call() } }

        assertEquals(StepUpPrompt(), gate.prompt.value)
        gate.submit("hunter2")
        assertEquals(Result.Success("done"), result.await())
    }

    @Test
    fun `a terminal step-up failure is returned without prompting again or running the call`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
            val signedOut = SyncError.SignedOut("session revoked")
            syncRepository.stepUpResults = listOf(Result.Failure(signedOut))

            val result = async { gate.run { call() } }
            gate.submit("hunter2")

            assertEquals(Result.Failure(signedOut), result.await())
            assertNull(gate.prompt.value)
            assertEquals(1, call.runs)
            assertEquals(listOf("hunter2"), syncRepository.stepUpPasswords)
        }

    @Test
    fun `a network failure during the step-up shows the error and accepts another try`() =
        runTestUnconfined {
            val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
            val network = SyncError.Network(NetworkFailure.Timeout)
            syncRepository.stepUpResults = listOf(Result.Failure(network), Result.Success(Unit))

            val result = async { gate.run { call() } }
            gate.submit("hunter2")

            assertEquals(StepUpPrompt(error = network), gate.prompt.value)
            assertFalse(result.isCompleted)

            gate.submit("hunter2")

            assertEquals(Result.Success("done"), result.await())
            assertEquals(2, call.runs)
        }

    @Test
    fun `submit and cancel without an open prompt are ignored`() = runTestUnconfined {
        gate.submit("hunter2")
        gate.cancel()

        val call = Call(Result.Failure(SyncError.FreshAuthRequired), Result.Success("done"))
        val result = async { gate.run { call() } }

        assertFalse(result.isCompleted)
        assertEquals(StepUpPrompt(), gate.prompt.value)
        gate.submit("hunter2")
        assertEquals(Result.Success("done"), result.await())
        assertEquals(listOf("hunter2"), syncRepository.stepUpPasswords)
    }
}

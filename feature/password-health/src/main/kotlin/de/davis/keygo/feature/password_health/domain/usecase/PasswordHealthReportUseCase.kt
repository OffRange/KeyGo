package de.davis.keygo.feature.password_health.domain.usecase

import android.util.Log
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.password_health.domain.LoginFingerprinter
import de.davis.keygo.feature.password_health.domain.PasswordHealthAttention
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.report.HealthReportAssembler
import de.davis.keygo.feature.password_health.domain.report.HealthReportScanner
import de.davis.keygo.feature.password_health.domain.repository.HealthReportStoreRepository
import de.davis.keygo.feature.password_health.domain.repository.HealthSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

@Single
class PasswordHealthReportUseCase(
    private val loginRepository: LoginRepository,
    private val loginFingerprinter: LoginFingerprinter,
    private val healthReportStoreRepository: HealthReportStoreRepository,
    private val healthSettingsRepository: HealthSettingsRepository,
    private val scanner: HealthReportScanner,
    private val assembler: HealthReportAssembler,
    private val attention: PasswordHealthAttention,
) {

    private val mutex = Mutex()

    suspend operator fun invoke(
        force: Boolean,
        coroutineContext: CoroutineContext = Dispatchers.Default,
    ): Result<PasswordHealthReport, PasswordHealthReportError> = mutex.withLock {
        report(force, coroutineContext)
            .onSuccess { attention.update(it.needsAttentionCount) }
            .onFailure { if (it == PasswordHealthReportError.NoPasswords) attention.clear() }
    }

    private suspend fun report(
        force: Boolean,
        coroutineContext: CoroutineContext,
    ): Result<PasswordHealthReport, PasswordHealthReportError> = resultBinding {
        withContext(coroutineContext) {
            val breachCheckEnabled = async {
                healthSettingsRepository.getBreachCheckState().breachesEnabled
            }
            val storedReport = async {
                if (force) null else healthReportStoreRepository.load().getOrNull()
            }

            val logins = loginRepository.observeLogins()
                .firstOrNull()
                ?.filter { it.passwordCredential != null }
                .asResult(PasswordHealthReportError.NoPasswords)
                .bind()

            val fingerprints = logins.map { login ->
                async { loginFingerprinter.fingerprint(login)?.let { login.id to it } }
            }
                .awaitAll()
                .filterNotNull()
                .toMap()

            val now = Clock.System.now()
            val previous = storedReport.await()
            previous?.takeIf { it.isFreshFor(fingerprints, breachCheckEnabled.await(), now) }
                ?.let { return@withContext assembler.assemble(it, logins) }

            val report = scanner.scan(
                logins = logins,
                fingerprints = fingerprints,
                previous = previous,
                breachCheckEnabled = breachCheckEnabled.await(),
                now = now,
            ).bind()

            // The store is only a cache, so failing to write it must not cost the user the report.
            healthReportStoreRepository.storeReport(report).onFailure {
                Log.w(TAG, "Failed to store password health report: $it")
            }
            assembler.assemble(report, logins)
        }
    }

    companion object {
        private const val TAG = "PasswordHealthReportUseCase"
    }
}

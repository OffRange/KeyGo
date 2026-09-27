package de.davis.keygo.feature.password_health.domain.usecase

import android.util.Log
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.mapSuccess
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.password_health.domain.HealthCheckNotifierScheduler
import de.davis.keygo.feature.password_health.domain.LoginFingerprinter
import de.davis.keygo.feature.password_health.domain.PasswordHealthAttention
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.VaultFingerprint
import de.davis.keygo.feature.password_health.domain.report.HealthReportAssembler
import de.davis.keygo.feature.password_health.domain.report.HealthReportScanner
import de.davis.keygo.feature.password_health.domain.repository.HealthNotificationStateRepository
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
    private val healthNotificationStateRepository: HealthNotificationStateRepository,
    private val healthCheckNotifierScheduler: HealthCheckNotifierScheduler,
) {

    private val mutex = Mutex()

    suspend operator fun invoke(
        force: Boolean,
        coroutineContext: CoroutineContext = Dispatchers.Default,
    ): Result<PasswordHealthReport, PasswordHealthReportError> = mutex.withLock {
        report(force, coroutineContext)
            .onSuccess { (report, vault) ->
                attention.update(report.needsAttentionCount)
                recordSnapshot(report.snapshot(vault))
            }
            .onFailure {
                if (it == PasswordHealthReportError.NoPasswords) {
                    attention.clear()
                    recordSnapshot(null)
                }
            }
            .mapSuccess { it.report }
    }

    private suspend fun recordSnapshot(snapshot: HealthSnapshot?) {
        if (!healthSettingsRepository.getBreachCheckState().notificationsEnabled) return

        healthNotificationStateRepository.setSnapshot(snapshot)
        // The setting outlives the schedule across a device restore; re-arming here brings it back.
        healthCheckNotifierScheduler.scheduleHealthReminder()
    }

    private suspend fun report(
        force: Boolean,
        coroutineContext: CoroutineContext,
    ): Result<Scan, PasswordHealthReportError> = resultBinding {
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

            val vault = VaultFingerprint.of(fingerprints.values)
            val now = Clock.System.now()
            val previous = storedReport.await()
            previous?.takeIf { it.isFreshFor(fingerprints, breachCheckEnabled.await(), now) }
                ?.let { return@withContext Scan(assembler.assemble(it, logins), vault) }

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
            Scan(assembler.assemble(report, logins), vault)
        }
    }

    private data class Scan(val report: PasswordHealthReport, val vault: VaultFingerprint)

    private fun PasswordHealthReport.snapshot(vault: VaultFingerprint) = HealthSnapshot(
        attentionCount = needsAttentionCount,
        breachedCount = (groups.flatMap { it.members } + standalone).count { it.breach != null },
        vault = vault,
    )

    companion object {
        private const val TAG = "PasswordHealthReportUseCase"
    }
}

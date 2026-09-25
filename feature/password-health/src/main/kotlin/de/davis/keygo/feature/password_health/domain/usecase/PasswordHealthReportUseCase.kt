package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.password_health.domain.LoginFingerprinter
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.report.HealthReportAssembler
import de.davis.keygo.feature.password_health.domain.report.HealthReportScanner
import de.davis.keygo.feature.password_health.domain.repository.BreachCheckStateRepository
import de.davis.keygo.feature.password_health.domain.repository.HealthReportStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

@Single
class PasswordHealthReportUseCase(
    private val loginRepository: LoginRepository,
    private val loginFingerprinter: LoginFingerprinter,
    private val healthReportStoreRepository: HealthReportStoreRepository,
    private val breachCheckStateRepository: BreachCheckStateRepository,
    private val scanner: HealthReportScanner,
    private val assembler: HealthReportAssembler,
) {

    suspend operator fun invoke(
        coroutineContext: CoroutineContext = Dispatchers.Default,
    ): Result<PasswordHealthReport, PasswordHealthReportError> = resultBinding {
        withContext(coroutineContext) {
            val breachCheckEnabled = async {
                breachCheckStateRepository.getBreachCheckState().enabled
            }
            val storedReport = async { healthReportStoreRepository.load().getOrNull() }

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
            storedReport.await()
                ?.takeIf { it.isFreshFor(fingerprints, breachCheckEnabled.await(), now) }
                ?.let { return@withContext assembler.assemble(it, logins) }

            val report = scanner.scan(logins, fingerprints, breachCheckEnabled.await(), now).bind()

            val stored = async { healthReportStoreRepository.storeReport(report) }
            val assembled = assembler.assemble(report, logins)
            stored.await().bind { PasswordHealthReportError.StoreFailed }
            assembled
        }
    }
}

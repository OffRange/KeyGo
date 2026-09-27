package de.davis.keygo.feature.password_health.domain

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.FakeHealthCheckNotifierScheduler
import de.davis.keygo.feature.password_health.FakeHealthNotificationStateRepository
import de.davis.keygo.feature.password_health.FakeHealthSettingsRepository
import de.davis.keygo.feature.password_health.data.LoginFingerprinterImpl
import de.davis.keygo.feature.password_health.domain.checker.PasswordHealthChecker
import de.davis.keygo.feature.password_health.domain.checker.WeakPasswordChecker
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.HealthReportStoreError
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import de.davis.keygo.feature.password_health.domain.report.HealthReportAssembler
import de.davis.keygo.feature.password_health.domain.report.HealthReportScanner
import de.davis.keygo.feature.password_health.domain.report.VAULT_ID
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.login
import de.davis.keygo.feature.password_health.domain.repository.HealthReportStoreRepository
import de.davis.keygo.feature.password_health.domain.usecase.PasswordHealthReportUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

// The use case does its work on Dispatchers.Default, which runTest's scheduler does not drive, so
// these run on real threads and wait on the observable count instead of on virtual time.
class UnlockHealthScanTest {

    private val session = FakeSession()
    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val breachState = FakeHealthSettingsRepository(breachesEnabled = false)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attention = PasswordHealthAttention(session, appScope)

    @AfterTest
    fun tearDown() = appScope.cancel()

    @Test
    fun unlockingScansTheVaultAndPublishesTheCount() = runBlocking(Dispatchers.Default) {
        seedWeak(0, 1)
        startWith()

        unlock()

        awaitCount(2)
    }

    @Test
    fun lockingClearsTheCount() = runBlocking(Dispatchers.Default) {
        seedWeak(0, 1)
        startWith()
        unlock()
        awaitCount(2)

        session.endSession()

        awaitCount(0)
    }

    @Test
    fun everyUnlockScansAgain() = runBlocking(Dispatchers.Default) {
        seedWeak(0)
        startWith()
        unlock()
        awaitCount(1)
        session.endSession()
        awaitCount(0)
        seedWeak(1)

        unlock()

        awaitCount(2)
    }

    @Test
    fun lockingCancelsAScanInFlight() = runBlocking(Dispatchers.Default) {
        seedWeak(0)
        val blocked = Blocking()
        startWith(listOf(blocked))
        unlock()
        withTimeout(TIMEOUT) { blocked.entered.await() }

        session.endSession()

        withTimeout(TIMEOUT) { blocked.cancelled.await() }
    }

    private fun startWith(
        checkers: List<PasswordHealthChecker> = listOf(WeakPasswordChecker()),
    ) = UnlockHealthScan(
        session = session,
        passwordHealth = PasswordHealthReportUseCase(
            loginRepository = loginRepository,
            loginFingerprinter = LoginFingerprinterImpl(),
            healthReportStoreRepository = InMemoryStore(),
            healthSettingsRepository = breachState,
            scanner = HealthReportScanner(FakeCryptographicScopeProvider(itemRepository), checkers),
            assembler = HealthReportAssembler(),
            attention = attention,
            healthNotificationStateRepository = FakeHealthNotificationStateRepository(),
            healthCheckNotifierScheduler = FakeHealthCheckNotifierScheduler(),
        ),
        attention = attention,
        appScope = appScope,
    )

    private suspend fun unlock() {
        session.unlockWithArk(ByteArray(ARK_SIZE))
    }

    private suspend fun awaitCount(count: Int) {
        withTimeout(TIMEOUT) { attention.needsAttention.first { it == count } }
    }

    private fun seedWeak(vararg ns: Int) = ns.forEach { n ->
        itemRepository.seedEnvelope(
            ItemKeyEnvelope(
                vaultId = VAULT_ID,
                itemId = id(n),
                itemKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
                vaultKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
            ),
        )
        loginRepository.seed(login(id(n), score = PasswordScore.Weak))
    }

    private class Blocking : PasswordHealthChecker {

        override val type = CheckKind.Strength

        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        override suspend fun check(candidates: List<PasswordCandidate>): CheckOutcome {
            entered.complete(Unit)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                cancelled.complete(Unit)
                throw e
            }
        }
    }

    private class InMemoryStore : HealthReportStoreRepository {
        private var stored: StoredHealthReport? = null

        override suspend fun storeReport(report: StoredHealthReport): Result<Unit, HealthReportStoreError> {
            stored = report
            return Result.Success(Unit)
        }

        override suspend fun load(): Result<StoredHealthReport, HealthReportStoreError> =
            stored?.let { Result.Success(it) }
                ?: Result.Failure(HealthReportStoreError.NoReportStored)
    }

    private companion object {
        val TIMEOUT = 5.seconds
        const val ARK_SIZE = 32
    }
}

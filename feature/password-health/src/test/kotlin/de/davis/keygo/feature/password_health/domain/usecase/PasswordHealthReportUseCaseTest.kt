package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.feature.password_health.FakeBreachCheckStateRepository
import de.davis.keygo.feature.password_health.FakeBreachedRepository
import de.davis.keygo.feature.password_health.FakeConnectivityRepository
import de.davis.keygo.feature.password_health.data.LoginFingerprinterImpl
import de.davis.keygo.feature.password_health.domain.PasswordHealthAttention
import de.davis.keygo.feature.password_health.domain.checker.BreachedPasswordCheck
import de.davis.keygo.feature.password_health.domain.checker.PasswordHealthChecker
import de.davis.keygo.feature.password_health.domain.checker.ReusePasswordCheck
import de.davis.keygo.feature.password_health.domain.checker.WeakPasswordChecker
import de.davis.keygo.feature.password_health.domain.model.BreachResult
import de.davis.keygo.feature.password_health.domain.model.BreachedError
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthReportStoreError
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.RelationType
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import de.davis.keygo.feature.password_health.domain.report.HealthReportAssembler
import de.davis.keygo.feature.password_health.domain.report.HealthReportScanner
import de.davis.keygo.feature.password_health.domain.report.VAULT_ID
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.login
import de.davis.keygo.feature.password_health.domain.repository.HealthReportStoreRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.Collections
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// Robolectric only so a failed store write can reach android.util.Log.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PasswordHealthReportUseCaseTest {

    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val scopeProvider = FakeCryptographicScopeProvider(itemRepository)
    private val fingerprinter = LoginFingerprinterImpl()
    private val store = FakeHealthReportStore()
    private val breachState = FakeBreachCheckStateRepository(enabled = true)
    private val breached = FakeBreachedRepository()
    private val attention = PasswordHealthAttention(
        session = FakeSession(startUnlocked = true),
        appScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private val strength = Counting(WeakPasswordChecker())
    private val reuse = Counting(ReusePasswordCheck())
    private val breach = Counting(
        BreachedPasswordCheck(breached, breachState, FakeConnectivityRepository()),
    )

    private val useCase = PasswordHealthReportUseCase(
        loginRepository = loginRepository,
        loginFingerprinter = fingerprinter,
        healthReportStoreRepository = store,
        breachCheckStateRepository = breachState,
        scanner = HealthReportScanner(scopeProvider, listOf(strength, reuse, breach)),
        assembler = HealthReportAssembler(),
        attention = attention,
    )

    @Test
    fun anEmptyVaultIsNoPasswords() = runTest {
        assertEquals(PasswordHealthReportError.NoPasswords, run().assertFailure())
    }

    @Test
    fun aVaultWithoutPasswordsIsNoPasswords() = runTest {
        loginRepository.seed(login(id(0)).copy(passwordCredential = null))

        assertEquals(PasswordHealthReportError.NoPasswords, run().assertFailure())
        assertTrue(strength.calls.isEmpty())
    }

    @Test
    fun loginsWithoutAPasswordAreLeftOut() = runTest {
        seed(0)
        loginRepository.seed(login(id(1)).copy(passwordCredential = null))

        val report = run().assertSuccess()

        assertEquals(1, report.totalPasswordsScanned)
        assertEquals(listOf(setOf(id(0))), strength.calls)
    }

    @Test
    fun aFirstRunScansEveryPasswordAndStoresTheResult() = runTest {
        seed(0, 1, score = PasswordScore.Weak)

        val report = run().assertSuccess()

        assertEquals(2, report.totalPasswordsScanned)
        assertEquals(setOf(id(0), id(1)), report.standalone.mapTo(mutableSetOf()) { it.itemId })
        assertEquals(
            listOf(ItemIssue.Weak(PasswordScore.Weak)),
            report.standalone.first().issues,
        )
        assertEquals(2, assertNotNull(store.stored).items.size)
        assertEquals(1, store.writes)
    }

    @Test
    fun reusedPasswordsFormAGroup() = runTest {
        seed(0, 1, password = "same")

        val group = run().assertSuccess().groups.single()

        assertEquals(RelationType.Reused, group.dominantRelation)
        assertEquals(setOf(id(0), id(1)), group.members.mapTo(mutableSetOf()) { it.itemId })
    }

    @Test
    fun onlyTheItemsThatNeedAttentionAreCounted() = runTest {
        seed(0, 1, score = PasswordScore.Weak)
        seed(2)

        run().assertSuccess()

        assertEquals(2, attention.needsAttention.value)
    }

    @Test
    fun everyMemberOfAGroupIsCountedNotTheGroup() = runTest {
        seed(0, 1, password = "same")
        seed(2, score = PasswordScore.Weak)

        run().assertSuccess()

        assertEquals(3, attention.needsAttention.value)
    }

    @Test
    fun aReportServedFromTheStoreIsCountedToo() = runTest {
        seed(0, 1, score = PasswordScore.Weak)
        run().assertSuccess()
        attention.clear()

        run().assertSuccess()

        assertEquals(1, store.writes)
        assertEquals(2, attention.needsAttention.value)
    }

    @Test
    fun aVaultThatBecameEmptyClearsTheCount() = runTest {
        attention.update(3)

        assertEquals(PasswordHealthReportError.NoPasswords, run().assertFailure())

        assertEquals(0, attention.needsAttention.value)
    }

    @Test
    fun breachedPasswordsAreReported() = runTest {
        seed(0, password = "password")
        breached.breaches = mapOf("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8" to 3)

        val item = run().assertSuccess().standalone.single()

        assertEquals(listOf(ItemIssue.Breached(3)), item.issues)
    }

    @Test
    fun aFreshStoredReportIsServedWithoutScanning() = runTest {
        seed(0, 1, score = PasswordScore.Weak)
        val first = run().assertSuccess()

        val second = run().assertSuccess()

        assertEquals(first, second)
        assertEquals(1, strength.calls.size)
        assertEquals(1, breach.calls.size)
        assertEquals(1, store.writes)
    }

    @Test
    fun aForcedRunScansEverythingAgain() = runTest {
        seed(0, 1)
        run().assertSuccess()

        run(force = true).assertSuccess()

        assertEquals(2, strength.calls.size)
        assertEquals(listOf(setOf(id(0), id(1)), setOf(id(0), id(1))), breach.calls)
        assertEquals(2, store.writes)
    }

    @Test
    fun anEditedPasswordTriggersAFreshScan() = runTest {
        seed(0, 1)
        run().assertSuccess()

        seed(1, password = "changed", score = PasswordScore.Weak)
        val report = run().assertSuccess()

        assertEquals(2, strength.calls.size)
        assertEquals(listOf(id(1)), report.standalone.map { it.itemId })
        assertEquals(setOf(id(1)), breach.calls.last())
    }

    @Test
    fun aNewLoginTriggersAFreshScan() = runTest {
        seed(0)
        run().assertSuccess()

        seed(1)
        val report = run().assertSuccess()

        assertEquals(2, report.totalPasswordsScanned)
        assertEquals(setOf(id(1)), breach.calls.last())
    }

    @Test
    fun aDeletedLoginDropsOutOfTheReport() = runTest {
        seed(0, 1, score = PasswordScore.Weak)
        run().assertSuccess()

        itemRepository.deleteItems(setOf(id(1)))
        val report = run().assertSuccess()

        assertEquals(1, report.totalPasswordsScanned)
        assertEquals(listOf(id(0)), report.standalone.map { it.itemId })
    }

    @Test
    fun anExpiredBreachLookupOnlyRepeatsTheBreachCheck() = runTest {
        seed(0, 1)
        store.stored = StoredHealthReport(
            items = listOf(0, 1).map { n ->
                StoredHealthItem(
                    id = id(n),
                    fingerprint = assertNotNull(fingerprinter.fingerprint(loginOf(n))),
                    score = null,
                    breach = BreachResult(0, Instant.fromEpochSeconds(0)),
                    unreadable = false,
                )
            },
            relationalFindings = emptyList(),
            breachCheckEnabled = true,
        )

        run().assertSuccess()

        assertTrue(strength.calls.isEmpty())
        assertEquals(listOf(setOf(id(0), id(1))), breach.calls)
        assertEquals(1, store.writes)
    }

    @Test
    fun turningTheBreachCheckOffRescansWithoutLookups() = runTest {
        seed(0)
        run().assertSuccess()

        breachState.setBreachEnabled(false)
        val report = run().assertSuccess()

        assertEquals(1, breached.calls.size)
        assertEquals(false, assertNotNull(store.stored).breachCheckEnabled)
        assertEquals(GapReason.Disabled, report.gaps[CheckKind.Breach]?.reason)
    }

    @Test
    fun anUnreachableBreachServiceIsReportedAsAGap() = runTest {
        seed(0)
        breached.failures[prefixOf(loginOf(0))] = BreachedError.Unreachable

        val report = run().assertSuccess()

        assertEquals(CheckGap(GapReason.Unreachable, setOf(id(0))), report.gaps[CheckKind.Breach])
    }

    @Test
    fun anUnreachableBreachServiceIsAskedAgainNextTime() = runTest {
        seed(0)
        breached.failures[prefixOf(loginOf(0))] = BreachedError.Unreachable
        run().assertSuccess()

        breached.failures.clear()
        val report = run().assertSuccess()

        assertEquals(emptyMap(), report.gaps)
        assertEquals(1, strength.calls.size)
        assertEquals(2, breach.calls.size)
    }

    @Test
    fun aReportThatCannotBeStoredIsStillReturned() = runTest {
        seed(0, score = PasswordScore.Weak)
        store.storeFailure = HealthReportStoreError.CryptoFailed

        val report = run().assertSuccess()

        assertEquals(listOf(id(0)), report.standalone.map { it.itemId })
        assertNull(store.stored)
    }

    @Test
    fun aStoredReportThatCannotBeReadIsScannedAgain() = runTest {
        seed(0)
        run().assertSuccess()
        store.loadFailure = HealthReportStoreError.CorruptedReport

        run().assertSuccess()

        assertEquals(2, strength.calls.size)
    }

    @Test
    fun anUnreadablePasswordIsReportedAndForcesTheNextScan() = runTest {
        seed(0)
        loginRepository.seed(login(id(1)))

        val report = run().assertSuccess()
        assertEquals(setOf(id(1)), report.unreadable)

        run().assertSuccess()
        assertEquals(2, strength.calls.size)
    }

    @Test
    fun aScanThatFailsLeavesTheCountAlone() = runTest {
        attention.update(3)
        loginRepository.seed(login(id(0)))

        assertEquals(PasswordHealthReportError.Unreadable, run().assertFailure())

        assertEquals(3, attention.needsAttention.value)
    }

    @Test
    fun callersThatOverlapShareOneScan() = runTest {
        seed(0)
        val gate = CompletableDeferred<Unit>()
        strength.gate = gate

        val first = async { run() }
        val second = async { run() }
        runCurrent()
        gate.complete(Unit)

        first.await().assertSuccess()
        second.await().assertSuccess()
        assertEquals(1, strength.calls.size)
    }

    private suspend fun run(force: Boolean = false) = useCase(force, EmptyCoroutineContext)

    /** Seeds logins whose passwords the fake provider can decrypt. */
    private fun seed(
        vararg ns: Int,
        password: String? = null,
        score: PasswordScore = PasswordScore.Strong,
    ) = ns.forEach { n ->
        itemRepository.seedEnvelope(
            ItemKeyEnvelope(
                vaultId = VAULT_ID,
                itemId = id(n),
                itemKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
                vaultKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
            ),
        )
        loginRepository.seed(
            if (password == null) login(id(n), score = score)
            else login(id(n), password = password, score = score),
        )
    }

    private suspend fun loginOf(n: Int): Login = assertNotNull(loginRepository.getLoginById(id(n)))

    /** The range the breach check asks about for [login]'s password. */
    private fun prefixOf(login: Login): String {
        val password = FakeCryptographicScopeProvider.transform(
            login.passwordCredential!!.secret.payload.ciphertext,
        )
        return MessageDigest.getInstance("SHA-1").digest(password)
            .joinToString("") { "%02X".format(it) }
            .take(5)
    }

    private class Counting(private val delegate: PasswordHealthChecker) : PasswordHealthChecker {

        override val type = delegate.type

        val calls: MutableList<Set<ItemId>> = Collections.synchronizedList(mutableListOf())

        var gate: CompletableDeferred<Unit>? = null

        override suspend fun check(candidates: List<PasswordCandidate>): CheckOutcome {
            calls += candidates.mapTo(mutableSetOf()) { it.id }
            gate?.await()
            return delegate.check(candidates)
        }
    }

    private class FakeHealthReportStore : HealthReportStoreRepository {

        var stored: StoredHealthReport? = null
        var writes = 0
        var storeFailure: HealthReportStoreError? = null
        var loadFailure: HealthReportStoreError? = null

        override suspend fun storeReport(report: StoredHealthReport): Result<Unit, HealthReportStoreError> {
            storeFailure?.let { return Result.Failure(it) }
            stored = report
            writes++
            return Result.Success(Unit)
        }

        override suspend fun load(): Result<StoredHealthReport, HealthReportStoreError> {
            loadFailure?.let { return Result.Failure(it) }
            return stored?.let { Result.Success(it) }
                ?: Result.Failure(HealthReportStoreError.NoReportStored)
        }
    }
}

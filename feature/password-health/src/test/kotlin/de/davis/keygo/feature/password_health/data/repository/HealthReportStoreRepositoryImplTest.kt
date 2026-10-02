package de.davis.keygo.feature.password_health.data.repository

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.BindingCryptographicScopeProvider
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthReportStore
import de.davis.keygo.feature.password_health.data.local.model.protoRelationFinding
import de.davis.keygo.feature.password_health.data.local.model.protoStoredHealthReport
import de.davis.keygo.feature.password_health.data.mapper.toCompressedByteArray
import de.davis.keygo.feature.password_health.data.mapper.toStoredHealthReport
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.HealthReportStoreError
import de.davis.keygo.feature.password_health.domain.model.RelationType
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import de.davis.keygo.feature.password_health.domain.report.breach
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.storedItem
import de.davis.keygo.rust.FakeItemManager
import de.davis.keygo.rust.FakeKeyWrapper
import de.davis.keygo.rust.FakeVaultManager
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class HealthReportStoreRepositoryImplTest {

    private val session = FakeSession(startUnlocked = true)
    private val provider = BindingCryptographicScopeProvider(
        session = session,
        itemRepository = FakeItemRepository(),
        itemManager = FakeItemManager(),
        keyWrapper = FakeKeyWrapper(),
        vaultManager = FakeVaultManager(),
    )
    private val dataStore = FakeDataStore(ProtoHealthReportStore.getDefaultInstance())
    private val repository = HealthReportStoreRepositoryImpl(dataStore, provider)

    private val report = StoredHealthReport(
        items = listOf(
            storedItem(id(0), score = PasswordScore.Weak, breach = breach(3, CHECKED_AT)),
            storedItem(id(1), breach = breach(0, CHECKED_AT)),
            storedItem(id(2), unreadable = true),
        ),
        relationalFindings = listOf(HealthFinding.Relation(setOf(id(0), id(1)), RelationType.Reused)),
        gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Unreachable, setOf(id(2)))),
        breachCheckEnabled = true,
    )

    @Test
    fun nothingStoredYetIsNoReport() = runTest {
        assertEquals(HealthReportStoreError.NoReportStored, repository.load().assertFailure())
    }

    @Test
    fun aStoredReportLoadsBackUnchanged() = runTest {
        repository.storeReport(report).assertSuccess()

        assertEquals(report, repository.load().assertSuccess())
    }

    @Test
    fun theReportIsNotStoredInTheClear() = runTest {
        repository.storeReport(report).assertSuccess()

        val stored = dataStore.state.value
        assertTrue(stored.wrappedKey.size() > 0)
        assertTrue(stored.iv.size() > 0)
        assertTrue(
            !stored.ciphertext.toByteArray().contentEquals(report.toCompressedByteArray()),
        )
    }

    @Test
    fun theLatestReportWins() = runTest {
        val newer = report.copy(breachCheckEnabled = false, gaps = emptyMap())

        repository.storeReport(report).assertSuccess()
        repository.storeReport(newer).assertSuccess()

        assertEquals(newer, repository.load().assertSuccess())
    }

    @Test
    fun laterReportsReuseTheFirstKey() = runTest {
        repository.storeReport(report).assertSuccess()
        val firstKey = dataStore.state.value.wrappedKey

        repository.storeReport(report.copy(breachCheckEnabled = false)).assertSuccess()

        assertEquals(firstKey, dataStore.state.value.wrappedKey)
    }

    @Test
    fun aLockedSessionCannotStoreAndLeavesTheStoreUntouched() = runTest {
        val locked = HealthReportStoreRepositoryImpl(
            dataStore,
            BindingCryptographicScopeProvider(
                session = FakeSession(startUnlocked = false),
                itemRepository = FakeItemRepository(),
                itemManager = FakeItemManager(),
                keyWrapper = FakeKeyWrapper(),
                vaultManager = FakeVaultManager(),
            ),
        )

        assertEquals(HealthReportStoreError.CryptoFailed, locked.storeReport(report).assertFailure())
        assertEquals(ProtoHealthReportStore.getDefaultInstance(), dataStore.state.value)
    }

    @Test
    fun aKeyThatWillNotUnwrapIsACryptoFailureOnStore() = runTest {
        repository.storeReport(report).assertSuccess()
        val before = dataStore.state.value
        session.unwrapVaultKeyFailure = SessionError.Locked

        assertEquals(HealthReportStoreError.CryptoFailed, repository.storeReport(report).assertFailure())
        assertEquals(before, dataStore.state.value)
    }

    @Test
    fun aKeyThatWillNotUnwrapIsACryptoFailureOnLoad() = runTest {
        repository.storeReport(report).assertSuccess()
        session.unwrapVaultKeyFailure = SessionError.Locked

        assertEquals(HealthReportStoreError.CryptoFailed, repository.load().assertFailure())
    }

    @Test
    fun aKeyWithoutAReportIsNoReport() = runTest {
        repository.storeReport(report).assertSuccess()
        dataStore.state.value = dataStore.state.value.toBuilder().clearCiphertext().build()

        assertEquals(HealthReportStoreError.NoReportStored, repository.load().assertFailure())
    }

    @Test
    fun aReportWithoutAKeyIsNoReport() = runTest {
        repository.storeReport(report).assertSuccess()
        dataStore.state.value = dataStore.state.value.toBuilder().clearWrappedKey().build()

        assertEquals(HealthReportStoreError.NoReportStored, repository.load().assertFailure())
    }

    @Test
    fun aReportFromAnotherAlgorithmVersionIsCorrupted() = runTest {
        repository.storeReport(report).assertSuccess()
        seal(
            protoStoredHealthReport {
                algorithmVersion = StoredHealthReport.ALGORITHM_VERSION + 1
            }.toByteArray().gzip(),
        )

        assertEquals(HealthReportStoreError.CorruptedReport, repository.load().assertFailure())
    }

    @Test
    fun theReportIsSealedUnderItsFixedNamespaceAndLabel() = runTest {
        repository.storeReport(report).assertSuccess()
        val stored = dataStore.state.value

        val opened = provider.accountScope(
            namespace = REPORT_NAMESPACE,
            wrapped = stored.wrappedKey(),
        ) {
            CryptographicData(stored.ciphertext.toByteArray(), stored.iv.toByteArray())
                .decrypt(REPORT_LABEL)
        }

        assertEquals(report, (opened as Result.Success).success.toStoredHealthReport())
    }

    @Test
    fun theKeyIsBoundToTheReportNamespace() = runTest {
        repository.storeReport(report).assertSuccess()

        val opened = provider.accountScope(
            namespace = UUID.randomUUID(),
            wrapped = dataStore.state.value.wrappedKey(),
        ) { }

        assertTrue(opened is Result.Failure)
    }

    @Test
    fun aTamperedCiphertextIsCorrupted() = runTest {
        repository.storeReport(report).assertSuccess()
        val stored = dataStore.state.value
        val tampered = stored.ciphertext.toByteArray().also { it[0] = (it[0] + 1).toByte() }
        dataStore.state.value = stored.toBuilder().setCiphertext(tampered.toByteString()).build()

        assertEquals(HealthReportStoreError.CorruptedReport, repository.load().assertFailure())
    }

    @Test
    fun aSwappedIvIsCorrupted() = runTest {
        repository.storeReport(report).assertSuccess()
        val stored = dataStore.state.value
        dataStore.state.value = stored.toBuilder()
            .setIv(ByteArray(stored.iv.size()).toByteString())
            .build()

        assertEquals(HealthReportStoreError.CorruptedReport, repository.load().assertFailure())
    }

    @Test
    fun aReportThatIsNotGzipIsCorrupted() = runTest {
        repository.storeReport(report).assertSuccess()
        seal(byteArrayOf(1, 2, 3))

        assertEquals(HealthReportStoreError.CorruptedReport, repository.load().assertFailure())
    }

    @Test
    fun aReportThatIsNotAProtoIsCorrupted() = runTest {
        repository.storeReport(report).assertSuccess()
        seal(byteArrayOf(0x0a, 0x7f, 0x01).gzip())

        assertEquals(HealthReportStoreError.CorruptedReport, repository.load().assertFailure())
    }

    @Test
    fun aReportPointingPastItsItemsIsCorrupted() = runTest {
        repository.storeReport(report).assertSuccess()
        seal(
            protoStoredHealthReport {
                algorithmVersion = StoredHealthReport.ALGORITHM_VERSION
                relationFinding += protoRelationFinding { itemRefs += listOf(0, 1) }
            }.toByteArray().gzip(),
        )

        assertEquals(HealthReportStoreError.CorruptedReport, repository.load().assertFailure())
    }

    @Test
    fun aCorruptedReportCanBeOverwritten() = runTest {
        repository.storeReport(report).assertSuccess()
        seal(byteArrayOf(1, 2, 3))

        repository.storeReport(report).assertSuccess()

        assertEquals(report, repository.load().assertSuccess())
    }

    /** Replaces the stored report with [plaintext], sealed the way the repository seals one. */
    private suspend fun seal(plaintext: ByteArray) {
        val stored = dataStore.state.value
        val sealed = provider.accountScope(REPORT_NAMESPACE, stored.wrappedKey()) {
            plaintext.encrypt(REPORT_LABEL)
        }.assertSuccess()

        dataStore.state.value = stored.toBuilder()
            .setCiphertext(sealed.data.toByteString())
            .setIv(sealed.iv.toByteString())
            .build()
    }

    private fun ProtoHealthReportStore.wrappedKey() = KeyInformation(
        wrappedKey = wrappedKey.toByteArray(),
        keyNonce = wrappedKeyNonce.toByteArray(),
    )

    private fun ByteArray.gzip(): ByteArray = ByteArrayOutputStream().also { bos ->
        GZIPOutputStream(bos).use { it.write(this) }
    }.toByteArray()

    private companion object {
        val CHECKED_AT: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)

        // Pinned on purpose: changing either makes every stored report undecryptable.
        val REPORT_NAMESPACE: UUID = UUID.fromString("39050172-ce6d-4f4d-8787-6e12ab44eb0e")
        const val REPORT_LABEL = "password_health_report"
    }
}

package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProviderFactory
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionFactory
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.rust.FakeArkSession
import de.davisalessandro.keygo.rust.ArkSession
import de.davisalessandro.keygo.rust.NoHandle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BackupArkUnlockerTest {

    private val vaultRepo = FakeVaultRepository()
    private val keyStore = FakeKeyStoreManager()
    private val arkStore = FakeBackupArkKeyStore()
    private val factory = FakeCryptographicScopeProviderFactory(
        FakeCryptographicScopeProvider(FakeItemRepository()),
    )

    /**
     * The escrow path exists to open, in a throwaway session, what the app session sealed, so the
     * factory hands back a genuinely separate session rather than the live one.
     */
    private fun unlocker(
        session: Session,
        sessionFactory: SessionFactory = SessionFactory { Session(FakeArkSession()) },
    ) = BackupArkUnlocker(
        session = session,
        sessionFactory = sessionFactory,
        keyStoreManager = keyStore,
        arkKeyStore = arkStore,
        scopeProviderFactory = factory,
        vaultRepository = vaultRepo,
    )

    private fun unlocked() = Session(FakeArkSession(startUnlocked = true))

    private fun locked() = Session(FakeArkSession())

    private suspend fun provision(ark: ByteArray) {
        val cipher = keyStore.getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Encrypt)
        arkStore.save(CryptographicData(cipher.doFinal(ark), cipher.iv))
    }

    @Test
    fun `unlocked session builds a scope on the live session`() = runTest {
        val session = unlocked()
        val result = unlocker(session).withScope { }
        assertIs<Result.Success<*, *>>(result)
        assertEquals(session, factory.lastSession)
    }

    @Test
    fun `locked and unprovisioned fails with NotProvisioned`() = runTest {
        val result = unlocker(locked()).withScope { }
        assertEquals(Result.Failure(ExportError.NotProvisioned), result)
    }

    @Test
    fun `locked but provisioned builds a scope on a throwaway session holding the ARK`() =
        runTest {
            val live = unlocked()
            val ark = assertNotNull(live.exportArk().getOrNull())
            provision(ark)

            // The throwaway session ends once the block returns, so assert from inside it.
            val result = unlocker(locked()).withScope {
                val used = assertNotNull(factory.lastSession)
                assertNotEquals(live, used)
                assertContentEquals(ark, used.exportArk().getOrNull())
            }

            assertIs<Result.Success<*, *>>(result)
        }

    @Test
    fun `locked provisioned but device locked fails with DeviceLocked`() = runTest {
        provision(ByteArray(32) { it.toByte() })
        keyStore.deviceLocked = true

        val result = unlocker(locked()).withScope { }
        assertEquals(Result.Failure(ExportError.DeviceLocked), result)
    }

    @Test
    fun `withSession hands over the live session itself`() = runTest {
        val session = unlocked()

        val result = unlocker(session).withSession { assertSame(session, it) }

        assertIs<Result.Success<*, *>>(result)
    }

    @Test
    fun `withSession recovers the provisioned ark into a throwaway session when locked`() =
        runTest {
            val ark = ByteArray(32) { (it + 1).toByte() }
            provision(ark)
            val throwaway = Session(FakeArkSession())

            val result = unlocker(locked(), SessionFactory { throwaway }).withSession {
                assertSame(throwaway, it)
                assertContentEquals(ark, it.exportArk().getOrNull())
            }

            assertIs<Result.Success<*, *>>(result)
        }

    @Test
    fun `withSession fails with NotProvisioned when locked and no ark copy exists`() = runTest {
        val result = unlocker(locked()).withSession { }

        val failure = assertIs<Result.Failure<Unit, ExportError>>(result)
        assertEquals(ExportError.NotProvisioned, failure.error)
    }

    @Test
    fun `the recovered ark is zeroed after use`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })
        val recorder = RecordingArkSession()

        unlocker(locked(), SessionFactory { Session(recorder) }).withSession {
            assertTrue(assertNotNull(recorder.handedOver).any { byte -> byte != 0.toByte() })
        }

        assertTrue(assertNotNull(recorder.handedOver).all { it == 0.toByte() })
    }

    @Test
    fun `the throwaway session is ended after use`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })
        val throwaway = Session(FakeArkSession())

        unlocker(locked(), SessionFactory { throwaway }).withSession { }

        assertFalse(throwaway.isActive.value)
    }

    @Test
    fun `a live session is left holding its own ark`() = runTest {
        // Ending the live session, or wiping its ARK, would be wiping the app's own session key.
        val session = unlocked()
        val before = assertNotNull(session.exportArk().getOrNull())

        unlocker(session).withSession { }

        assertTrue(session.isActive.value)
        assertContentEquals(before, session.exportArk().getOrNull())
    }
}

/**
 * Keeps the array it is handed instead of copying it, so a test can watch the caller wipe it.
 * Extends the generated class through uniffi's `NoHandle` constructor the same way `FakeArkSession`
 * does: no Rust object is allocated and the native library is never touched.
 */
private class RecordingArkSession : ArkSession(NoHandle) {

    var handedOver: ByteArray? = null
        private set

    private var active = false

    override fun unlockWithArk(ark: ByteArray) {
        handedOver = ark
        active = true
    }

    override fun isActive(): Boolean = active

    override fun end() {
        active = false
    }
}

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
import de.davis.keygo.rust.RecordingArkSession
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

    /**
     * The recovered ARK is in hand before the throwaway session exists, so everything from that
     * point on has to sit inside the wipe guard. This is the observable half: the recorder keeps
     * the array it was handed, then fails, and the array still comes back zeroed.
     */
    @Test
    fun `the recovered ark is zeroed when unlocking the throwaway session fails`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })
        val recorder = RecordingArkSession().apply { failUnlock = true }

        val result = unlocker(locked(), SessionFactory { Session(recorder) }).withSession { }

        assertIs<Result.Failure<Unit, ExportError>>(result)
        assertTrue(assertNotNull(recorder.handedOver).all { it == 0.toByte() })
    }

    /**
     * The other half, which no assertion can watch directly because the array never leaves
     * `withSession` on this path: a factory that throws must not escape without the wipe running.
     * Creating the session inside the guard is what makes that true, so this pins the propagation
     * and leaves the wipe itself to the test above.
     */
    @Test
    fun `a throwing session factory propagates without leaving a session behind`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })
        val live = locked()

        val thrown = runCatching {
            unlocker(live, SessionFactory { error("no session for you") }).withSession { }
        }

        assertTrue(thrown.isFailure)
        assertFalse(live.isActive.value)
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

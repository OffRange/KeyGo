@file:OptIn(ExportArk::class)

package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProviderFactory
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.ExportArk
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.useArk
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.domain.model.ExportError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun unlocker(
        session: Session,
    ) = BackupArkUnlocker(
        session = session,
        keyStoreManager = keyStore,
        arkKeyStore = arkStore,
        scopeProviderFactory = factory,
        vaultRepository = vaultRepo,
    )

    private fun unlocked() = FakeSession(startUnlocked = true)

    private fun locked() = FakeSession()

    private suspend fun provision(ark: ByteArray) {
        val cipher = keyStore.getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Encrypt)
        arkStore.save(CryptographicData(cipher.doFinal(ark), cipher.iv))
    }

    @Test
    fun `unlocked session builds a scope on the live session`() = runTest {
        val session = unlocked()
        unlocker(session).withScope { }.assertSuccess()
        assertEquals(session, factory.lastSession)
    }

    @Test
    fun `locked and unprovisioned fails with NotProvisioned`() = runTest {
        val result = unlocker(locked()).withScope { }.assertFailure()
        assertEquals(ExportError.NotProvisioned, result)
    }

    @Test
    fun `locked but provisioned builds a scope on the session holding the recovered ARK`() =
        runTest {
            val live = unlocked()
            live.useArk { ark ->
                provision(ark)
                val session = locked()

                unlocker(session).withScope {
                    val used = assertNotNull(factory.lastSession)
                    assertSame(session, used)
                    assertNotEquals(live, used)

                    used.useArk { lastArk ->
                        assertContentEquals(ark, lastArk)
                    }
                }.assertSuccess()
            }.assertSuccess()
        }

    @Test
    fun `locked provisioned but device locked fails with DeviceLocked`() = runTest {
        provision(ByteArray(32) { it.toByte() })
        keyStore.deviceLocked = true

        val result = unlocker(locked()).withScope { }.assertFailure()
        assertEquals(ExportError.DeviceLocked, result)
    }

    @Test
    fun `withSession hands over the live session itself`() = runTest {
        val session = unlocked()

        unlocker(session).withSession { assertSame(session, it) }.assertSuccess()
    }

    @Test
    fun `withSession recovers the provisioned ark into the session when locked`() = runTest {
        val ark = ByteArray(32) { (it + 1).toByte() }
        provision(ark)
        val session = locked()

        unlocker(session).withSession {
            assertSame(session, it)
            it.useArk { sessionArk ->
                assertContentEquals(ark, sessionArk)
            }.assertSuccess()
        }.assertSuccess()
    }

    @Test
    fun `withSession fails with NotProvisioned when locked and no ark copy exists`() = runTest {
        val result = unlocker(locked()).withSession { }.assertFailure()
        assertEquals(ExportError.NotProvisioned, result)
    }

    @Test
    fun `the recovered ark is zeroed after use`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })
        val recorder = FakeSession()

        unlocker(recorder).withSession {
            assertTrue(assertNotNull(recorder.handedOver).any { byte -> byte != 0.toByte() })
        }

        assertTrue(assertNotNull(recorder.handedOver).all { it == 0.toByte() })
    }

    @Test
    fun `the session is ended after use when it was not already active`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })
        val session = locked()

        unlocker(session).withSession { }

        assertFalse(session.isActive.value)
    }

    /**
     * The recovered ARK is in hand before `unlockWithArk` runs, so everything from that point on
     * has to sit inside the wipe guard. This is the observable half: the recorder keeps the array
     * it was handed, then fails, and the array still comes back zeroed.
     */
    @Test
    fun `the recovered ark is zeroed when unlocking the session fails`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })
        val recorder = FakeSession().apply { failUnlock = true }

        unlocker(recorder)
            .withSession { }
            .assertFailure()

        assertTrue(assertNotNull(recorder.handedOver).all { it == 0.toByte() })
    }

    @Test
    fun `a live session is left holding its own ark`() = runTest {
        // Ending the live session, or wiping its ARK, would be wiping the app's own session key.
        val session = unlocked()
        val before = assertNotNull(session.exportArk().getOrNull())

        unlocker(session).withSession { }.assertSuccess()

        assertTrue(session.isActive.value)
        assertContentEquals(before, session.exportArk().getOrNull())
    }
}

@file:OptIn(ExportArk::class)

package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.FakeSessionFactory
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
import de.davis.keygo.feature.backup.domain.model.retryable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BackupArkUnlockerTest {

    private val keyStore = FakeKeyStoreManager()
    private val arkStore = FakeBackupArkKeyStore()
    private val sessionFactory = FakeSessionFactory()

    private fun unlocker(session: Session) = BackupArkUnlocker(
        session = session,
        sessionFactory = sessionFactory,
        keyStoreManager = keyStore,
        arkKeyStore = arkStore,
    )

    private fun unlocked() = FakeSession(startUnlocked = true)

    private fun locked() = FakeSession()

    private fun throwaway(): FakeSession = sessionFactory.created.single()

    private suspend fun provision(ark: ByteArray = ByteArray(32) { (it + 1).toByte() }) {
        val cipher = keyStore.getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Encrypt)
        arkStore.save(CryptographicData(cipher.doFinal(ark), cipher.iv))
    }

    @Test
    fun `withSession hands over the live session itself`() = runTest {
        val session = unlocked()

        unlocker(session).withSession { assertSame(session, it) }.assertSuccess()

        assertTrue(sessionFactory.created.isEmpty())
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

    @Test
    fun `withSession recovers the provisioned ark into a throwaway session when locked`() =
        runTest {
            val ark = ByteArray(32) { (it + 1).toByte() }
            provision(ark)

            unlocker(locked()).withSession {
                assertSame(throwaway(), it)
                it.useArk { sessionArk -> assertContentEquals(ark, sessionArk) }.assertSuccess()
            }.assertSuccess()
        }

    @Test
    fun `the app-wide session stays locked while a backup runs on the escrowed ark`() = runTest {
        provision()
        val session = locked()

        unlocker(session).withSession {
            assertNotSame(session, it)
            assertFalse(session.isActive.value)
        }.assertSuccess()

        assertFalse(session.isActive.value)
    }

    @Test
    fun `a user unlocking during a backup keeps their session when it finishes`() = runTest {
        provision()
        val session = locked()
        val userArk = ByteArray(32) { (it + 50).toByte() }

        unlocker(session).withSession {
            session.unlockWithArk(userArk.copyOf()).assertSuccess()
        }.assertSuccess()

        assertTrue(session.isActive.value)
        assertContentEquals(userArk, session.exportArk().getOrNull())
    }

    @Test
    fun `withSession fails with NotProvisioned when locked and no ark copy exists`() = runTest {
        val result = unlocker(locked()).withSession { }.assertFailure()
        assertEquals(ExportError.NotProvisioned, result)
    }

    @Test
    fun `locked provisioned but device locked fails with DeviceLocked`() = runTest {
        provision()
        keyStore.deviceLocked = true

        val result = unlocker(locked()).withSession { }.assertFailure()
        assertEquals(ExportError.DeviceLocked, result)
    }

    @Test
    fun `an escrowed ark the session rejects fails as CryptoFailed, not a retry`() = runTest {
        provision(ByteArray(16) { (it + 1).toByte() })

        val result = unlocker(locked()).withSession { }.assertFailure()

        assertEquals(ExportError.CryptoFailed, result)
        assertFalse(result.retryable)
    }

    @Test
    fun `the recovered ark is zeroed after use`() = runTest {
        provision()

        unlocker(locked()).withSession {
            assertTrue(assertNotNull(throwaway().handedOver).any { byte -> byte != 0.toByte() })
        }

        assertTrue(assertNotNull(throwaway().handedOver).all { it == 0.toByte() })
    }

    @Test
    fun `the throwaway session is ended after use`() = runTest {
        provision()

        unlocker(locked()).withSession { }.assertSuccess()

        assertFalse(throwaway().isActive.value)
    }

    @Test
    fun `the throwaway session is ended when the block throws`() = runTest {
        provision()

        runCatching { unlocker(locked()).withSession { error("boom") } }

        assertFalse(throwaway().isActive.value)
    }

    @Test
    fun `the recovered ark is zeroed when unlocking the session fails`() = runTest {
        provision()
        sessionFactory.failUnlock = true

        unlocker(locked()).withSession { }.assertFailure()

        assertTrue(assertNotNull(throwaway().handedOver).all { it == 0.toByte() })
    }
}

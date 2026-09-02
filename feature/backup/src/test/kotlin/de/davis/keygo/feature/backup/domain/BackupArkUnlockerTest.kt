package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProviderFactory
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.data.BackupSession
import de.davis.keygo.feature.backup.domain.model.ExportError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupArkUnlockerTest {

    private val vaultRepo = FakeVaultRepository()
    private val keyStore = FakeKeyStoreManager()
    private val arkStore = FakeBackupArkKeyStore()
    private val factory = FakeCryptographicScopeProviderFactory(
        FakeCryptographicScopeProvider(FakeItemRepository()),
    )

    private fun unlocker(session: FakeSession) = BackupArkUnlocker(
        session = session,
        keyStoreManager = keyStore,
        arkKeyStore = arkStore,
        scopeProviderFactory = factory,
        vaultRepository = vaultRepo,
    )

    private suspend fun provision(ark: ByteArray) {
        val cipher = keyStore.getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Encrypt)
        arkStore.save(CryptographicData(cipher.doFinal(ark), cipher.iv))
    }

    @Test
    fun `unlocked session builds a scope on the live session`() = runTest {
        val session = FakeSession(startOnConstruct = true)
        val result = unlocker(session).withScope { }
        assertIs<Result.Success<*, *>>(result)
        assertEquals(session, factory.lastSession)
    }

    @Test
    fun `locked and unprovisioned fails with NotProvisioned`() = runTest {
        val result = unlocker(FakeSession(startOnConstruct = false)).withScope { }
        assertEquals(Result.Failure(ExportError.NotProvisioned), result)
    }

    @Test
    fun `locked but provisioned recovers the ARK into a BackupSession`() = runTest {
        val ark = ByteArray(32) { (it + 1).toByte() }
        provision(ark)

        // The recovered ARK is zeroed once the block returns, so assert on it from inside.
        val result = unlocker(FakeSession(startOnConstruct = false)).withScope {
            val used = factory.lastSession
            assertIs<BackupSession>(used)
            assertContentEquals(ark, used.ark)
        }

        assertIs<Result.Success<*, *>>(result)
    }

    @Test
    fun `locked provisioned but device locked fails with DeviceLocked`() = runTest {
        provision(ByteArray(32) { it.toByte() })
        keyStore.deviceLocked = true

        val result = unlocker(FakeSession(startOnConstruct = false)).withScope { }
        assertEquals(Result.Failure(ExportError.DeviceLocked), result)
    }

    @Test
    fun `withArk hands over the live session ark`() = runTest {
        val session = FakeSession(startOnConstruct = true)
        val expected = assertNotNull(session.ark).copyOf()

        val result = unlocker(session).withArk { assertContentEquals(expected, it) }

        assertIs<Result.Success<*, *>>(result)
    }

    @Test
    fun `withArk recovers the provisioned ark when locked`() = runTest {
        val ark = ByteArray(32) { (it + 1).toByte() }
        provision(ark)

        val result = unlocker(FakeSession(startOnConstruct = false)).withArk {
            assertContentEquals(ark, it)
        }

        assertIs<Result.Success<*, *>>(result)
    }

    @Test
    fun `withArk fails with NotProvisioned when locked and no ark copy exists`() = runTest {
        val result = unlocker(FakeSession(startOnConstruct = false)).withArk { }

        val failure = assertIs<Result.Failure<Unit, ExportError>>(result)
        assertEquals(ExportError.NotProvisioned, failure.error)
    }

    @Test
    fun `a recovered ark is zeroed after use`() = runTest {
        provision(ByteArray(32) { (it + 1).toByte() })

        var seen: ByteArray? = null
        unlocker(FakeSession(startOnConstruct = false)).withArk { ark ->
            seen = ark
            assertTrue(ark.any { it != 0.toByte() })
        }

        assertTrue(assertNotNull(seen).all { it == 0.toByte() })
    }

    @Test
    fun `a recovered ark is zeroed after use in withScope`() = runTest {
        val ark = ByteArray(32) { (it + 1).toByte() }
        provision(ark)

        val result = unlocker(FakeSession(startOnConstruct = false)).withScope {
            val used = factory.lastSession
            assertIs<BackupSession>(used)
            assertContentEquals(ark, used.ark)
        }

        assertIs<Result.Success<*, *>>(result)

        val used = factory.lastSession
        assertIs<BackupSession>(used)
        assertTrue(assertNotNull(used.ark).all { it == 0.toByte() })
    }

    @Test
    fun `a live session ark is left intact`() = runTest {
        // FakeSession seeds ByteArray(32) { it.toByte() } - zeroing it would be zeroing the app's
        // own session key.
        val session = FakeSession(startOnConstruct = true)

        unlocker(session).withArk { }

        assertTrue(assertNotNull(session.ark).any { it != 0.toByte() })
    }
}

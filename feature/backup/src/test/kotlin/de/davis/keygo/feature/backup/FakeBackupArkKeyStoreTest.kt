package de.davis.keygo.feature.backup

import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeBackupArkKeyStoreTest {

    @Test
    fun `load returns null before anything is saved`() = runTest {
        assertNull(FakeBackupArkKeyStore().load())
    }

    @Test
    fun `save then load round-trips the data`() = runTest {
        val store = FakeBackupArkKeyStore()
        val data = CryptographicData(byteArrayOf(1, 2, 3), byteArrayOf(9, 8))
        store.save(data)
        assertEquals(data, store.load())
    }

    @Test
    fun `clear removes stored data and counts`() = runTest {
        val store = FakeBackupArkKeyStore(CryptographicData(byteArrayOf(1), byteArrayOf(2)))
        store.clear()
        assertNull(store.load())
        assertEquals(1, store.clearCount)
    }
}

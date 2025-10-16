package de.davis.keygo.auth.data.repository

import de.davis.keygo.core.identity.biometric.data.repository.BiometricKekRepositoryImpl
import de.davis.keygo.core.identity.biometric.domain.model.KeyStoreError
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.KeyStore
import javax.crypto.SecretKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BiometricKekRepositoryImplTest {

    private lateinit var keyStore: KeyStore
    private lateinit var repository: BiometricKekRepositoryImpl

    @BeforeTest
    fun setUp() {
        keyStore = mockk()
        repository = BiometricKekRepositoryImpl(keyStore)
    }

    @Test
    fun `test hasKek returns false when no KEK exists`() {
        every { keyStore.containsAlias(BiometricKekRepositoryImpl.KEY_ALIAS) } returns false
        assertFalse(repository.hasKek())
        verify { keyStore.containsAlias(BiometricKekRepositoryImpl.KEY_ALIAS) }
    }

    @Test
    fun `test hasKek returns true when KEK exists`() {
        every { keyStore.containsAlias(BiometricKekRepositoryImpl.KEY_ALIAS) } returns true
        assertTrue(repository.hasKek())
        verify { keyStore.containsAlias(BiometricKekRepositoryImpl.KEY_ALIAS) }
    }

    @Test
    fun `test getKek returns AES key when exists`() {
        val key = mockk<SecretKey>()
        every { keyStore.getKey(BiometricKekRepositoryImpl.KEY_ALIAS, null) } returns key

        val kek = repository.getKek()

        verify { keyStore.getKey(BiometricKekRepositoryImpl.KEY_ALIAS, null) }
        assertTrue(kek.isSuccess())
        assertNotNull(kek.getOrNull())
    }

    @Test
    fun `test getKek returns KeyNotFound when KEK does not exist`() {
        every { keyStore.getKey(BiometricKekRepositoryImpl.KEY_ALIAS, null) } returns null

        val kek = repository.getKek()

        verify { keyStore.getKey(BiometricKekRepositoryImpl.KEY_ALIAS, null) }
        assertTrue(kek.isFailure())
        assertEquals(expected = KeyStoreError.KeyNotFound, actual = kek.error)
    }
}
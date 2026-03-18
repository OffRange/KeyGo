package de.davis.keygo.feature.credentials.domain.usecase

import de.davis.keygo.core.item.domain.model.PasskeyMetadata
import de.davis.keygo.core.item.domain.model.PasskeyUser
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetPasskeysTest {

    private val passkeyRepository = mockk<PasskeyRepository>()
    private val getPasskeys = GetPasskeys(passkeyRepository)
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    private fun metadata(
        credentialId: ByteArray,
        rpId: String = "example.com",
        vaultName: String = "Test",
        userName: String = "user"
    ) = PasskeyMetadata(
        passwordUsername = null,
        vaultName = vaultName,
        user = PasskeyUser(name = userName, displayName = userName),
        credentialId = credentialId
    )

    @Test
    fun `returns all passkeys for RP when no allowCredentials specified`() = runTest {
        val cred1 = byteArrayOf(1, 2, 3)
        val cred2 = byteArrayOf(4, 5, 6)
        coEvery { passkeyRepository.getPasskeysForRP("example.com") } returns listOf(
            metadata(cred1),
            metadata(cred2)
        )

        val requestJson = """{"rpId":"example.com"}"""
        val result = getPasskeys(requestJson)

        assertEquals(2, result.size)
    }

    @Test
    fun `returns all passkeys when allowCredentials is empty list`() = runTest {
        val cred1 = byteArrayOf(1, 2, 3)
        coEvery { passkeyRepository.getPasskeysForRP("example.com") } returns listOf(
            metadata(cred1)
        )

        val requestJson = """{"rpId":"example.com","allowCredentials":[]}"""
        val result = getPasskeys(requestJson)

        assertEquals(1, result.size)
    }

    @Test
    fun `filters passkeys by allowCredentials`() = runTest {
        val cred1 = byteArrayOf(1, 2, 3)
        val cred2 = byteArrayOf(4, 5, 6)
        val cred1Base64 = base64.encode(cred1)
        coEvery { passkeyRepository.getPasskeysForRP("example.com") } returns listOf(
            metadata(cred1),
            metadata(cred2)
        )

        val requestJson =
            """{"rpId":"example.com","allowCredentials":[{"type":"public-key","id":"$cred1Base64"}]}"""
        val result = getPasskeys(requestJson)

        assertEquals(1, result.size)
        assertTrue(result[0].credentialId.contentEquals(cred1))
    }

    @Test
    fun `returns empty when no matching credentials`() = runTest {
        val cred1 = byteArrayOf(1, 2, 3)
        val nonExistent = byteArrayOf(9, 9, 9)
        val nonExistentBase64 = base64.encode(nonExistent)
        coEvery { passkeyRepository.getPasskeysForRP("example.com") } returns listOf(
            metadata(cred1)
        )

        val requestJson =
            """{"rpId":"example.com","allowCredentials":[{"type":"public-key","id":"$nonExistentBase64"}]}"""
        val result = getPasskeys(requestJson)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `queries correct RP ID from request`() = runTest {
        coEvery { passkeyRepository.getPasskeysForRP("login.example.org") } returns emptyList()

        val requestJson = """{"rpId":"login.example.org"}"""
        val result = getPasskeys(requestJson)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `ignores unknown JSON fields`() = runTest {
        coEvery { passkeyRepository.getPasskeysForRP("example.com") } returns listOf(
            metadata(byteArrayOf(1))
        )

        val requestJson =
            """{"rpId":"example.com","timeout":60000,"challenge":"abc","userVerification":"required"}"""
        val result = getPasskeys(requestJson)

        assertEquals(1, result.size)
    }

    @Test
    fun `handles multiple allowCredentials matching subset`() = runTest {
        val cred1 = byteArrayOf(1, 2, 3)
        val cred2 = byteArrayOf(4, 5, 6)
        val cred3 = byteArrayOf(7, 8, 9)
        val cred1Base64 = base64.encode(cred1)
        val cred3Base64 = base64.encode(cred3)
        coEvery { passkeyRepository.getPasskeysForRP("example.com") } returns listOf(
            metadata(cred1),
            metadata(cred2),
            metadata(cred3)
        )

        val requestJson =
            """{"rpId":"example.com","allowCredentials":[{"type":"public-key","id":"$cred1Base64"},{"type":"public-key","id":"$cred3Base64"}]}"""
        val result = getPasskeys(requestJson)

        assertEquals(2, result.size)
    }
}

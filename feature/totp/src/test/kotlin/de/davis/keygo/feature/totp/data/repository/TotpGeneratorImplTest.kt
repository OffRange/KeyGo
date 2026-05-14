package de.davis.keygo.feature.totp.data.repository

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.rust.FakeTotpService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TotpGeneratorImplTest {

    private val totpService = FakeTotpService()
    private val fakeItemRepository = FakeItemRepository()
    private val cryptographicScopeProvider = FakeCryptographicScopeProvider(fakeItemRepository)

    private val generator = TotpGeneratorImpl(
        totpService = totpService,
        cryptographicScopeProvider = cryptographicScopeProvider,
    )

    @Test
    fun `observeTotpCode(Totp) returns empty flow when item envelope not found`() = runTest {
        val totp = testTotp()
        // fakeItemRepository has no envelope seeded

        val result = generator.observeTotpCode(totp).toList()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `observeTotpCode(Totp) emits TotpValue when envelope is seeded`() = runTest {
        val encryptedSecret = FakeCryptographicScopeProvider.transform(SECRET.toByteArray())

        val loginId = newItemId()
        val vaultId = newVaultId()
        val totp = testTotp(loginId = loginId, secret = encryptedSecret)

        fakeItemRepository.seedEnvelope(
            ItemKeyEnvelope(
                vaultId = vaultId,
                itemId = loginId,
                itemKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
                vaultKeyInformation = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
            )
        )

        val expectedCode = "654321"
        totpService.totpResult = expectedCode

        val firstEmission = generator.observeTotpCode(totp).first()
        val value = (firstEmission as Result.Success).success

        assertEquals(expectedCode, value.code)
        assertEquals(30_000L, value.maxLifetime)
        assertTrue(value.validUntil > System.currentTimeMillis())
    }

    private fun testTotp(
        loginId: ItemId = newItemId(),
        secret: ByteArray = byteArrayOf(),
    ) = Totp(
        loginId = loginId,
        secret = Totp.Secret(EncryptedPayload(ciphertext = secret, iv = byteArrayOf())),
        accountName = "alice",
        issuer = "example",
        algorithm = "sha1",
        digits = 6,
        period = 30,
    )

    companion object {
        private const val SECRET = "JBSWY3DPEHPK3PXP"
    }
}

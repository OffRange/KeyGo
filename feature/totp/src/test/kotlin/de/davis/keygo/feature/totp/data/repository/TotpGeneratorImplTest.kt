package de.davis.keygo.feature.totp.data.repository

import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
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
    private val cryptographicScopeProvider = FakeCryptographicScopeProvider()
    private val loginRepository = FakeLoginRepository()
    private val vaultRepository = FakeVaultRepository()

    private val generator = TotpGeneratorImpl(
        totpService = totpService,
        cryptographicScopeProvider = cryptographicScopeProvider,
        loginRepository = loginRepository,
        vaultRepository = vaultRepository,
    )

    @Test
    fun `observeTotpCode(Totp) returns empty flow when login not found`() = runTest {
        val totp = testTotp()
        // loginRepository is empty

        val result = generator.observeTotpCode(totp).toList()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `observeTotpCode(Totp) returns empty flow when vault key info not found`() = runTest {
        val totp = testTotp()
        val login = testLogin(id = totp.loginId)
        loginRepository.seed(login)
        // vaultRepository is empty

        val result = generator.observeTotpCode(totp).toList()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `observeTotpCode(Totp) emits TotpInformation when all data present`() = runTest {
        val encryptedSecret = FakeCryptographicScopeProvider.transform(SECRET.toByteArray())

        val totp = testTotp(secret = encryptedSecret)
        val vaultId = newVaultId()
        val login = testLogin(id = totp.loginId, vaultId = vaultId)
        val vault = testVault(id = vaultId)

        loginRepository.seed(login)
        vaultRepository.seed(vault)

        val expectedCode = "654321"
        totpService.totpResult = expectedCode

        val firstEmission = generator.observeTotpCode(totp).first()

        assertEquals(expectedCode, firstEmission.code)
        assertEquals(30000L, firstEmission.maxLifetime)
        assertTrue(firstEmission.validUntil > System.currentTimeMillis())
    }
    
    private fun testLogin(
        id: ItemId = newItemId(),
        vaultId: VaultId = newVaultId(),
    ) = Login(
        id = id,
        username = "user",
        domainInfos = emptySet(),
        passwordCredential = PasswordCredential( // TODO(#43-task3)
            secret = PasswordSecret(EncryptedPayload.EMPTY),
            score = PasswordScore.Strong,
        ),
        totp = null,
        name = "Test",
        note = null,
        pinned = false,
        vaultId = vaultId,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    private fun testVault(
        id: VaultId = newVaultId()
    ) = Vault(
        id = id,
        name = "Vault",
        icon = Vault.Icon.Person,
        keyInformation = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
    )

    private fun testTotp(
        loginId: ItemId = newItemId(),
        secret: ByteArray = byteArrayOf()
    ) = Totp(
        loginId = loginId,
        secret = Totp.Secret(EncryptedPayload(ciphertext = secret, iv = byteArrayOf())),
        accountName = "alice",
        issuer = "example",
        algorithm = "sha1",
        digits = 6,
        period = 30
    )

    companion object {
        private const val SECRET = "JBSWY3DPEHPK3PXP"
    }
}

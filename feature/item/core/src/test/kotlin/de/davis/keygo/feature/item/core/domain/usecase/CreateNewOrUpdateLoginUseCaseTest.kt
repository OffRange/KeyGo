package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.item.core.domain.model.LoginError
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.clear
import de.davis.keygo.feature.item.core.domain.model.set
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateNewOrUpdateLoginUseCaseTest {

    private val defaultVault = Vault(
        id = newVaultId(),
        name = "Default vault",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    private val cryptoProvider = FakeCryptographicScopeProvider()
    private val vaultRepository = FakeVaultRepository()
    private val loginRepository = FakeLoginRepository()
    private val useCase = makeUseCase(loginRepository, vaultRepository)

    @BeforeTest
    fun setupVault() = runTest {
        vaultRepository.seed(defaultVault)
    }


    // Validation — Create

    @Test
    fun `create with blank name returns BlankName error`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "", password = "secret")
        )

        assertTrue(result.isFailure())
        assertEquals(LoginError.BlankName, result.error.single())
    }

    @Test
    fun `create with whitespace-only name returns BlankName error`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "   ", password = "secret")
        )

        assertTrue(result.isFailure())
        assertEquals(LoginError.BlankName, result.error.single())
    }

    @Test
    fun `create with blank password returns BlankPassword error`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "")
        )

        assertTrue(result.isFailure())
        assertEquals(LoginError.BlankPassword, result.error.single())
    }

    @Test
    fun `create with blank name and blank password returns both errors`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "", password = "")
        )

        assertTrue(result.isFailure())
        assertContains(result.error, LoginError.BlankName)
        assertContains(result.error, LoginError.BlankPassword)
        assertEquals(2, result.error.size)
    }

    // Validation — Update

    @Test
    fun `update with Keep name and Keep password is valid`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id))

        assertTrue(result.isSuccess())
    }

    @Test
    fun `update with Clear name returns BlankName error`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, name = clear()))

        assertTrue(result.isFailure())
        assertEquals(LoginError.BlankName, result.error.single())
    }

    @Test
    fun `update with blank Set name returns BlankName error`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, name = set("")))

        assertTrue(result.isFailure())
        assertEquals(LoginError.BlankName, result.error.single())
    }

    @Test
    fun `update with Clear password returns BlankPassword error`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, password = clear()))

        assertTrue(result.isFailure())
        assertEquals(LoginError.BlankPassword, result.error.single())
    }

    // Success — Create

    @Test
    fun `create with valid fields returns Success`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        assertTrue(result.isSuccess())
    }

    @Test
    fun `create stores login with correct name`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)
        assertEquals("My site", stored.name)
    }

    @Test
    fun `create with optional username and note stores them`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = "s3cr3t",
                username = "user@example.com",
                note = "A note",
            )
        )

        val stored = storedById(result.getOrNull())
        assertEquals("user@example.com", stored?.username)
        assertEquals("A note", stored?.note)
    }

    @Test
    fun `create without optional fields stores nulls`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        val stored = storedById(result.getOrNull())
        assertEquals(null, stored?.username)
        assertEquals(null, stored?.note)
        assertEquals(null, stored?.totp)
    }

    @Test
    fun `create stores password strength from estimator`() = runTest {
        val freshLoginRepo = FakeLoginRepository()
        val freshVaultRepo = FakeVaultRepository()
        freshVaultRepo.seed(defaultVault)

        val localUseCase = makeUseCase(
            loginRepository = freshLoginRepo,
            vaultRepository = freshVaultRepo,
            estimator = FakePasswordStrengthEstimator(PasswordScore.Weak),
        )

        val result = localUseCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "123")
        )

        val stored = freshLoginRepo.getLoginById(result.getOrNull()!!)
        assertEquals(PasswordScore.Weak, stored?.passwordScore)
    }

    @Test
    fun `update with new password re-evaluates password strength`() = runTest {
        val existing = testLogin(passwordScore = PasswordScore.Weak)
        loginRepository.seed(existing)

        // inject an estimator that returns Strong
        val localUseCase = makeUseCase(
            estimator = FakePasswordStrengthEstimator(PasswordScore.Strong)
        )

        localUseCase(UpsertLogin.update(itemId = existing.id, password = set("SuperS3cr3t!")))

        val updated = loginRepository.getLoginById(existing.id)
        assertEquals(PasswordScore.Strong, updated?.passwordScore)
    }

    // Success — Update

    @Test
    fun `update with new name replaces name`() = runTest {
        val existing = testLogin(name = "Old name")
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, name = set("New name")))

        assertEquals("New name", loginRepository.getLoginById(existing.id)?.name)
    }

    @Test
    fun `update with Keep name preserves existing name`() = runTest {
        val existing = testLogin(name = "Preserved")
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id))

        assertEquals("Preserved", loginRepository.getLoginById(existing.id)?.name)
    }

    @Test
    fun `update with new username replaces username`() = runTest {
        val existing = testLogin(username = "old@example.com")
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, username = set("new@example.com")))

        assertEquals("new@example.com", loginRepository.getLoginById(existing.id)?.username)
    }

    @Test
    fun `update with Clear username sets username to null`() = runTest {
        val existing = testLogin(username = "user@example.com")
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, username = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(null, loginRepository.getLoginById(existing.id)?.username)
    }

    @Test
    fun `update with Clear totpSecret removes totp`() = runTest {
        val base = testLogin()
        val existing = base.copy(
            totp = Totp(loginId = base.id, secret = Totp.Secret(EncryptedPayload.EMPTY)),
        )
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, totpSecret = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(null, loginRepository.getLoginById(existing.id)?.totp)
    }

    // Failure

    @Test
    fun `update with unknown id returns InvalidItemId error`() = runTest {
        val result = useCase(UpsertLogin.update(itemId = newItemId()))

        assertTrue(result.isFailure())
        assertContains(result.error, LoginError.InvalidItemId)
    }

    // Vault move on update

    @Test
    fun `update with different vaultId moves item to that vault`() = runTest {
        val otherVault = defaultVault.copy(id = newVaultId(), name = "Other vault")
        vaultRepository.seed(otherVault)

        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(itemId = existing.id, vaultId = otherVault.id)
        )

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(otherVault.id, loginRepository.getLoginById(existing.id)?.vaultId)
    }

    @Test
    fun `update with same vaultId keeps item in original vault`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, vaultId = defaultVault.id))

        assertEquals(defaultVault.id, loginRepository.getLoginById(existing.id)?.vaultId)
    }

    @Test
    fun `update with null vaultId keeps item in original vault`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, name = set("Renamed")))

        assertEquals(defaultVault.id, loginRepository.getLoginById(existing.id)?.vaultId)
    }

    @Test
    fun `update with unknown target vaultId returns InvalidVaultId error`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(itemId = existing.id, vaultId = newVaultId())
        )

        assertTrue(result.isFailure())
        assertContains(result.error, LoginError.InvalidVaultId)
    }

    @Test
    fun `update moving vaults also applies field changes`() = runTest {
        val otherVault = defaultVault.copy(id = newVaultId(), name = "Other vault")
        vaultRepository.seed(otherVault)

        val existing = testLogin(name = "Old name")
        loginRepository.seed(existing)

        useCase(
            UpsertLogin.update(
                itemId = existing.id,
                vaultId = otherVault.id,
                name = set("New name"),
            )
        )

        val stored = loginRepository.getLoginById(existing.id)
        assertEquals(otherVault.id, stored?.vaultId)
        assertEquals("New name", stored?.name)
    }

    @Test
    fun `update with different vaultId rewraps item key under the destination vault`() = runTest {
        val otherVault = Vault(
            id = newVaultId(),
            name = "Other vault",
            keyInformation = KeyInformation(
                wrappedKey = byteArrayOf(0x0A),
                keyNonce = byteArrayOf(0x0B),
            ),
            icon = Vault.Icon.Default,
        )
        vaultRepository.seed(otherVault)

        val existingItemKey = KeyInformation(
            wrappedKey = byteArrayOf(0x11, 0x22),
            keyNonce = byteArrayOf(0x33, 0x44),
        )
        val existing = testLogin().copy(keyInformation = existingItemKey)
        loginRepository.seed(existing)

        val rewrappedItemKey = KeyInformation(
            wrappedKey = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
            keyNonce = byteArrayOf(0xCC.toByte(), 0xDD.toByte()),
        )
        cryptoProvider.rewrapResult = Result.Success(rewrappedItemKey)

        val result = useCase(
            UpsertLogin.update(itemId = existing.id, vaultId = otherVault.id)
        )

        assertTrue(result.isSuccess(), "result: $result")

        val rewrap = cryptoProvider.rewrapCalls.single()
        assertEquals(defaultVault.id, rewrap.sourceVault.vaultId)
        assertContentEquals(
            defaultVault.keyInformation.wrappedKey,
            rewrap.sourceVault.wrappedVaultKey.wrappedKey,
        )
        assertEquals(otherVault.id, rewrap.destinationVault.vaultId)
        assertContentEquals(
            otherVault.keyInformation.wrappedKey,
            rewrap.destinationVault.wrappedVaultKey.wrappedKey,
        )
        assertEquals(existing.id, rewrap.sourceItem.itemAad.itemId)
        assertEquals(defaultVault.id, rewrap.sourceItem.itemAad.vaultId)
        assertContentEquals(
            existingItemKey.wrappedKey,
            rewrap.sourceItem.wrappedItemKey?.wrappedKey,
        )

        val stored = loginRepository.getLoginById(existing.id)
        assertNotNull(stored)
        assertEquals(otherVault.id, stored.vaultId)
        assertContentEquals(rewrappedItemKey.wrappedKey, stored.keyInformation.wrappedKey)
        assertContentEquals(rewrappedItemKey.keyNonce, stored.keyInformation.keyNonce)
    }

    @Test
    fun `update with same vaultId does not rewrap`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, vaultId = defaultVault.id))

        assertTrue(cryptoProvider.rewrapCalls.isEmpty())
    }

    // Crypto scope

    @Test
    fun `create routes secret fields through the crypto scope`() = runTest {
        val plaintextPassword = "s3cr3t"
        val plaintextTotp = "JBSWY3DPEHPK3PXP"

        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = plaintextPassword,
                totpSecret = plaintextTotp,
            )
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)

        val labels = cryptoProvider.encryptCalls.map { it.label }
        assertContains(labels, PasswordSecret.label)
        assertContains(labels, Totp.Secret.label)
        assertEquals(2, labels.size)

        val passwordCall = cryptoProvider.encryptCalls.single { it.label == PasswordSecret.label }
        assertContentEquals(plaintextPassword.encodeToByteArray(), passwordCall.plaintext)

        val totpCall = cryptoProvider.encryptCalls.single { it.label == Totp.Secret.label }
        assertContentEquals(plaintextTotp.encodeToByteArray(), totpCall.plaintext)

        assertFalse(stored.password.payload.ciphertext.contentEquals(plaintextPassword.encodeToByteArray()))
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextPassword.encodeToByteArray()),
            stored.password.payload.ciphertext
        )
        assertContentEquals(FakeCryptographicScopeProvider.IV, stored.password.payload.iv)

        assertNotNull(stored.totp)
        assertFalse(stored.totp!!.secret.payload.ciphertext.contentEquals(plaintextTotp.encodeToByteArray()))
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextTotp.encodeToByteArray()),
            stored.totp!!.secret.payload.ciphertext
        )
        assertContentEquals(FakeCryptographicScopeProvider.IV, stored.totp!!.secret.payload.iv)
    }

    @Test
    fun `repository failure on create wraps error in DatabaseError`() = runTest {
        val cause = RuntimeException("disk full")
        loginRepository.createOrUpdateError = cause

        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        assertTrue(result.isFailure())
        val error = assertIs<LoginError.DatabaseError>(result.error.single())
        assertEquals(cause, error.throwable)
    }

    // Helpers

    private fun makeUseCase(
        loginRepository: FakeLoginRepository = this@CreateNewOrUpdateLoginUseCaseTest.loginRepository,
        vaultRepository: FakeVaultRepository = this@CreateNewOrUpdateLoginUseCaseTest.vaultRepository,
        estimator: FakePasswordStrengthEstimator = FakePasswordStrengthEstimator(),
        cryptographicScopeProvider: CryptographicScopeProvider = cryptoProvider,
    ) = CreateNewOrUpdateLoginUseCase(
        cryptographicScopeProvider = cryptographicScopeProvider,
        loginRepository = loginRepository,
        vaultRepository = vaultRepository,
        upsertVaultItem = UpsertVaultItemUseCase(loginRepository),
        passwordStrengthEstimator = estimator,
    )

    private fun testLogin(
        name: String = "Test",
        username: String? = null,
        passwordScore: PasswordScore = PasswordScore.Strong,
    ) = Login(
        id = newItemId(),
        name = name,
        username = username,
        domainInfos = emptySet(),
        passwordScore = passwordScore,
        password = PasswordSecret(EncryptedPayload.EMPTY),
        totp = null,
        note = null,
        pinned = false,
        vaultId = defaultVault.id,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    private suspend fun storedById(id: ItemId?): Login? {
        id ?: return null
        return loginRepository.getLoginById(id)
    }
}

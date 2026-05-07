package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.FakePasswordRepository
import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.item.core.domain.model.PasswordError
import de.davis.keygo.feature.item.core.domain.model.UpsertPassword
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
    private val passwordRepository = FakePasswordRepository()
    private val useCase = makeUseCase(passwordRepository, vaultRepository)

    @BeforeTest
    fun setupVault() = runTest {
        vaultRepository.seed(defaultVault)
    }


    // Validation — Create

    @Test
    fun `create with blank name returns BlankName error`() = runTest {
        val result = useCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "", password = "secret")
        )

        assertTrue(result.isFailure())
        assertEquals(PasswordError.BlankName, result.error.single())
    }

    @Test
    fun `create with whitespace-only name returns BlankName error`() = runTest {
        val result = useCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "   ", password = "secret")
        )

        assertTrue(result.isFailure())
        assertEquals(PasswordError.BlankName, result.error.single())
    }

    @Test
    fun `create with blank password returns BlankPassword error`() = runTest {
        val result = useCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "My site", password = "")
        )

        assertTrue(result.isFailure())
        assertEquals(PasswordError.BlankPassword, result.error.single())
    }

    @Test
    fun `create with blank name and blank password returns both errors`() = runTest {
        val result = useCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "", password = "")
        )

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.BlankName)
        assertContains(result.error, PasswordError.BlankPassword)
        assertEquals(2, result.error.size)
    }

    // Validation — Update

    @Test
    fun `update with Keep name and Keep password is valid`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(itemId = existing.id))

        assertTrue(result.isSuccess())
    }

    @Test
    fun `update with Clear name returns BlankName error`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(itemId = existing.id, name = clear()))

        assertTrue(result.isFailure())
        assertEquals(PasswordError.BlankName, result.error.single())
    }

    @Test
    fun `update with blank Set name returns BlankName error`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(itemId = existing.id, name = set("")))

        assertTrue(result.isFailure())
        assertEquals(PasswordError.BlankName, result.error.single())
    }

    @Test
    fun `update with Clear password returns BlankPassword error`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(itemId = existing.id, password = clear()))

        assertTrue(result.isFailure())
        assertEquals(PasswordError.BlankPassword, result.error.single())
    }

    // Success — Create

    @Test
    fun `create with valid fields returns Success`() = runTest {
        val result = useCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        assertTrue(result.isSuccess())
    }

    @Test
    fun `create stores password with correct name`() = runTest {
        val result = useCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)
        assertEquals("My site", stored.name)
    }

    @Test
    fun `create with optional username and note stores them`() = runTest {
        val result = useCase(
            UpsertPassword.create(
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
            UpsertPassword.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        val stored = storedById(result.getOrNull())
        assertEquals(null, stored?.username)
        assertEquals(null, stored?.note)
        assertEquals(null, stored?.totp)
    }

    @Test
    fun `create stores password strength from estimator`() = runTest {
        val freshPasswordRepo = FakePasswordRepository()
        val freshVaultRepo = FakeVaultRepository()
        freshVaultRepo.seed(defaultVault)

        val localUseCase = makeUseCase(
            passwordRepository = freshPasswordRepo,
            vaultRepository = freshVaultRepo,
            estimator = FakePasswordStrengthEstimator(Login.Score.Weak),
        )

        val result = localUseCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "My site", password = "123")
        )

        val stored = freshPasswordRepo.getPasswordById(result.getOrNull()!!)
        assertEquals(Login.Score.Weak, stored?.score)
    }

    @Test
    fun `update with new password re-evaluates password strength`() = runTest {
        val existing = testPassword(score = Login.Score.Weak)
        passwordRepository.seed(existing)

        // inject an estimator that returns Strong
        val localUseCase = makeUseCase(
            estimator = FakePasswordStrengthEstimator(Login.Score.Strong)
        )

        localUseCase(UpsertPassword.update(itemId = existing.id, password = set("SuperS3cr3t!")))

        val updated = passwordRepository.getPasswordById(existing.id)
        assertEquals(Login.Score.Strong, updated?.score)
    }

    // Success — Update

    @Test
    fun `update with new name replaces name`() = runTest {
        val existing = testPassword(name = "Old name")
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(itemId = existing.id, name = set("New name")))

        assertEquals("New name", passwordRepository.getPasswordById(existing.id)?.name)
    }

    @Test
    fun `update with Keep name preserves existing name`() = runTest {
        val existing = testPassword(name = "Preserved")
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(itemId = existing.id))

        assertEquals("Preserved", passwordRepository.getPasswordById(existing.id)?.name)
    }

    @Test
    fun `update with new username replaces username`() = runTest {
        val existing = testPassword(username = "old@example.com")
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(itemId = existing.id, username = set("new@example.com")))

        assertEquals("new@example.com", passwordRepository.getPasswordById(existing.id)?.username)
    }

    @Test
    fun `update with Clear username sets username to null`() = runTest {
        val existing = testPassword(username = "user@example.com")
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(itemId = existing.id, username = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(null, passwordRepository.getPasswordById(existing.id)?.username)
    }

    // Failure

    @Test
    fun `update with unknown id returns InvalidItemId error`() = runTest {
        val result = useCase(UpsertPassword.update(itemId = newItemId()))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.InvalidItemId)
    }

    // Vault move on update

    @Test
    fun `update with different vaultId moves item to that vault`() = runTest {
        val otherVault = defaultVault.copy(id = newVaultId(), name = "Other vault")
        vaultRepository.seed(otherVault)

        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(
            UpsertPassword.update(itemId = existing.id, vaultId = otherVault.id)
        )

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(otherVault.id, passwordRepository.getPasswordById(existing.id)?.vaultId)
    }

    @Test
    fun `update with same vaultId keeps item in original vault`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(itemId = existing.id, vaultId = defaultVault.id))

        assertEquals(defaultVault.id, passwordRepository.getPasswordById(existing.id)?.vaultId)
    }

    @Test
    fun `update with null vaultId keeps item in original vault`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(itemId = existing.id, name = set("Renamed")))

        assertEquals(defaultVault.id, passwordRepository.getPasswordById(existing.id)?.vaultId)
    }

    @Test
    fun `update with unknown target vaultId returns InvalidVaultId error`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(
            UpsertPassword.update(itemId = existing.id, vaultId = newVaultId())
        )

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.InvalidVaultId)
    }

    @Test
    fun `update moving vaults also applies field changes`() = runTest {
        val otherVault = defaultVault.copy(id = newVaultId(), name = "Other vault")
        vaultRepository.seed(otherVault)

        val existing = testPassword(name = "Old name")
        passwordRepository.seed(existing)

        useCase(
            UpsertPassword.update(
                itemId = existing.id,
                vaultId = otherVault.id,
                name = set("New name"),
            )
        )

        val stored = passwordRepository.getPasswordById(existing.id)
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
        val existing = testPassword().copy(keyInformation = existingItemKey)
        passwordRepository.seed(existing)

        val rewrappedItemKey = KeyInformation(
            wrappedKey = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
            keyNonce = byteArrayOf(0xCC.toByte(), 0xDD.toByte()),
        )
        cryptoProvider.rewrapResult = Result.Success(rewrappedItemKey)

        val result = useCase(
            UpsertPassword.update(itemId = existing.id, vaultId = otherVault.id)
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

        val stored = passwordRepository.getPasswordById(existing.id)
        assertNotNull(stored)
        assertEquals(otherVault.id, stored.vaultId)
        assertContentEquals(rewrappedItemKey.wrappedKey, stored.keyInformation.wrappedKey)
        assertContentEquals(rewrappedItemKey.keyNonce, stored.keyInformation.keyNonce)
    }

    @Test
    fun `update with same vaultId does not rewrap`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(itemId = existing.id, vaultId = defaultVault.id))

        assertTrue(cryptoProvider.rewrapCalls.isEmpty())
    }

    // Crypto scope

    @Test
    fun `create routes secret fields through the crypto scope`() = runTest {
        val plaintextPassword = "s3cr3t"
        val plaintextTotp = "JBSWY3DPEHPK3PXP"

        val result = useCase(
            UpsertPassword.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = plaintextPassword,
                totpSecret = plaintextTotp,
            )
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)

        val labels = cryptoProvider.encryptCalls.map { it.label }
        assertContains(labels, Login.LABEL_PASSWORD)
        assertContains(labels, Login.LABEL_TOTP_SECRET)
        assertEquals(2, labels.size)

        val passwordCall =
            cryptoProvider.encryptCalls.single { it.label == Login.LABEL_PASSWORD }
        assertContentEquals(plaintextPassword.encodeToByteArray(), passwordCall.plaintext)

        val totpCall =
            cryptoProvider.encryptCalls.single { it.label == Login.LABEL_TOTP_SECRET }
        assertContentEquals(plaintextTotp.encodeToByteArray(), totpCall.plaintext)

        assertFalse(stored.password.data.contentEquals(plaintextPassword.encodeToByteArray()))
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextPassword.encodeToByteArray()),
            stored.password.data
        )
        assertContentEquals(FakeCryptographicScopeProvider.IV, stored.password.iv)

        assertNotNull(stored.totp)
        assertFalse(stored.totp!!.secret.data.contentEquals(plaintextTotp.encodeToByteArray()))
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextTotp.encodeToByteArray()),
            stored.totp!!.secret.data
        )
    }

    @Test
    fun `repository failure on create wraps error in DatabaseError`() = runTest {
        val cause = RuntimeException("disk full")
        passwordRepository.createOrUpdateError = cause

        val result = useCase(
            UpsertPassword.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        assertTrue(result.isFailure())
        val error = assertIs<PasswordError.DatabaseError>(result.error.single())
        assertEquals(cause, error.throwable)
    }

    // Helpers

    private fun makeUseCase(
        passwordRepository: FakePasswordRepository = this@CreateNewOrUpdateLoginUseCaseTest.passwordRepository,
        vaultRepository: FakeVaultRepository = this@CreateNewOrUpdateLoginUseCaseTest.vaultRepository,
        estimator: FakePasswordStrengthEstimator = FakePasswordStrengthEstimator(),
        cryptographicScopeProvider: CryptographicScopeProvider = cryptoProvider,
    ) = CreateNewOrUpdatePasswordUseCase(
        cryptographicScopeProvider = cryptographicScopeProvider,
        passwordRepository = passwordRepository,
        vaultRepository = vaultRepository,
        upsertVaultItem = UpsertVaultItemUseCase(passwordRepository),
        passwordStrengthEstimator = estimator,
    )

    private fun testPassword(
        name: String = "Test",
        username: String? = null,
        score: Login.Score = Login.Score.Strong,
    ) = Login(
        id = newItemId(),
        name = name,
        username = username,
        domainInfos = emptySet(),
        score = score,
        password = SecretData.EMPTY_STRING,
        totp = null,
        note = null,
        pinned = false,
        vaultId = defaultVault.id,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    private suspend fun storedById(id: ItemId?): Login? {
        id ?: return null
        return passwordRepository.getPasswordById(id)
    }
}

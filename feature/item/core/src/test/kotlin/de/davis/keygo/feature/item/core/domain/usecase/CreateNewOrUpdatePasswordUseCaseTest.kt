package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.FakeCryptographicScopeProvider
import de.davis.keygo.core.item.FakePasswordRepository
import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.item.core.domain.model.PasswordError
import de.davis.keygo.feature.item.core.domain.model.UpsertPassword
import de.davis.keygo.feature.item.core.domain.model.clear
import de.davis.keygo.feature.item.core.domain.model.set
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateNewOrUpdatePasswordUseCaseTest {

    private val cryptoProvider = FakeCryptographicScopeProvider()
    private val passwordRepository = FakePasswordRepository()
    private val useCase = makeUseCase(passwordRepository)

    // Validation — Create

    @Test
    fun `create with blank name returns BlankName error`() = runTest {
        val result = useCase(UpsertPassword.create(name = "", password = "secret"))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.BlankName)
    }

    @Test
    fun `create with whitespace-only name returns BlankName error`() = runTest {
        val result = useCase(UpsertPassword.create(name = "   ", password = "secret"))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.BlankName)
    }

    @Test
    fun `create with blank password returns BlankPassword error`() = runTest {
        val result = useCase(UpsertPassword.create(name = "My site", password = ""))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.BlankPassword)
    }

    @Test
    fun `create with blank name and blank password returns both errors`() = runTest {
        val result = useCase(UpsertPassword.create(name = "", password = ""))

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

        val result = useCase(UpsertPassword.update(vaultId = existing.id))

        assertTrue(result.isSuccess())
    }

    @Test
    fun `update with Clear name returns BlankName error`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(vaultId = existing.id, name = clear()))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.BlankName)
    }

    @Test
    fun `update with blank Set name returns BlankName error`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(vaultId = existing.id, name = set("")))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.BlankName)
    }

    @Test
    fun `update with Clear password returns BlankPassword error`() = runTest {
        val existing = testPassword()
        passwordRepository.seed(existing)

        val result = useCase(UpsertPassword.update(vaultId = existing.id, password = clear()))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.BlankPassword)
    }

    // Success — Create

    @Test
    fun `create with valid fields returns Success`() = runTest {
        val result = useCase(UpsertPassword.create(name = "My site", password = "s3cr3t"))

        assertTrue(result.isSuccess())
    }

    @Test
    fun `create stores password with correct name`() = runTest {
        val result = useCase(UpsertPassword.create(name = "My site", password = "s3cr3t"))

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)
        assertEquals("My site", stored.name)
    }

    @Test
    fun `create with optional username and note stores them`() = runTest {
        val result = useCase(
            UpsertPassword.create(
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
        val result = useCase(UpsertPassword.create(name = "My site", password = "s3cr3t"))

        val stored = storedById(result.getOrNull())
        assertEquals(null, stored?.username)
        assertEquals(null, stored?.note)
        assertEquals(null, stored?.totpSecret)
    }

    @Test
    fun `create stores password strength from estimator`() = runTest {
        val freshRepo = FakePasswordRepository()
        val localUseCase = makeUseCase(
            repo = freshRepo,
            estimator = FakePasswordStrengthEstimator(Password.Score.Weak),
        )

        val result = localUseCase(UpsertPassword.create(name = "My site", password = "123"))

        val stored = freshRepo.getPasswordById(result.getOrNull()!!)
        assertEquals(Password.Score.Weak, stored?.score)
    }

    // Success — Update

    @Test
    fun `update with new name replaces name`() = runTest {
        val existing = testPassword(name = "Old name")
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(vaultId = existing.id, name = set("New name")))

        assertEquals("New name", passwordRepository.getPasswordById(existing.id)?.name)
    }

    @Test
    fun `update with Keep name preserves existing name`() = runTest {
        val existing = testPassword(name = "Preserved")
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(vaultId = existing.id))

        assertEquals("Preserved", passwordRepository.getPasswordById(existing.id)?.name)
    }

    @Test
    fun `update with new username replaces username`() = runTest {
        val existing = testPassword(username = "old@example.com")
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(vaultId = existing.id, username = set("new@example.com")))

        assertEquals("new@example.com", passwordRepository.getPasswordById(existing.id)?.username)
    }

    @Test
    fun `update with Clear username sets username to null`() = runTest {
        val existing = testPassword(username = "user@example.com")
        passwordRepository.seed(existing)

        useCase(UpsertPassword.update(vaultId = existing.id, username = clear()))

        assertEquals(null, passwordRepository.getPasswordById(existing.id)?.username)
    }

    // Failure

    @Test
    fun `update with unknown id returns InvalidVaultId error`() = runTest {
        val result = useCase(UpsertPassword.update(vaultId = newItemId()))

        assertTrue(result.isFailure())
        assertContains(result.error, PasswordError.InvalidVaultId)
    }

    @Test
    fun `repository failure on create wraps error in DatabaseError`() = runTest {
        val cause = RuntimeException("disk full")
        passwordRepository.createOrUpdateError = cause

        val result = useCase(UpsertPassword.create(name = "My site", password = "s3cr3t"))

        assertTrue(result.isFailure())
        val error = result.error.single()
        assertTrue(error is PasswordError.DatabaseError)
        assertEquals(cause, error.throwable)
    }

    // Helpers

    private fun makeUseCase(
        repo: FakePasswordRepository = passwordRepository,
        estimator: FakePasswordStrengthEstimator = FakePasswordStrengthEstimator(),
    ) = CreateNewOrUpdatePasswordUseCase(
        cryptographicScopeProvider = cryptoProvider,
        passwordRepository = repo,
        upsertVaultItem = UpsertVaultItemUseCase(repo),
        passwordStrengthEstimator = estimator,
    )

    private fun testPassword(
        name: String = "Test",
        username: String? = null,
    ) = Password(
        id = newItemId(),
        name = name,
        username = username,
        domainInfos = emptySet(),
        score = Password.Score.Strong,
        password = SecretData.EMPTY_STRING,
        totpSecret = null,
        note = null,
        pinned = false,
        vaultId = newVaultId(),
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    private suspend fun storedById(id: ItemId?): Password? {
        id ?: return null
        return passwordRepository.getPasswordById(id)
    }
}

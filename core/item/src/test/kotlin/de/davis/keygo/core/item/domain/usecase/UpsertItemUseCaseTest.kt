package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.FakePasswordRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpsertItemUseCaseTest {

    private val passwordRepository = FakePasswordRepository()
    private val useCase = UpsertVaultItemUseCase(passwordRepository)

    private fun testPassword(name: String = "Test") = Password(
        id = newItemId(),
        username = "user",
        domainInfos = emptySet(),
        score = Password.Score.Strong,
        totp = null,
        name = name,
        password = SecretData.EMPTY_STRING,
        note = null,
        pinned = false,
        vaultId = newVaultId(),
        keyInformation = KeyInformation(
            wrappedKey = byteArrayOf(),
            keyNonce = byteArrayOf(),
        ),
    )

    @Test
    fun `delegates password to passwordRepository`() = runTest {
        val password = testPassword()

        useCase(password)

        assertNotNull(passwordRepository.getPasswordById(password.id))
    }

    @Test
    fun `returns success with item id`() = runTest {
        val password = testPassword()

        val result = useCase(password)

        assertTrue(result.isSuccess())
        assertEquals(password.id, result.success)
    }

    @Test
    fun `returns failure from repository`() = runTest {
        val error = RuntimeException("db error")
        passwordRepository.createOrUpdateError = error

        val result = useCase(testPassword())

        assertTrue(result.isFailure())
        assertEquals(error, result.error)
    }
}

package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpsertVaultItemUseCaseTest {

    private val passwordRepository = mockk<PasswordRepository>()
    private val useCase = UpsertVaultItemUseCase(passwordRepository)

    private fun testPassword(name: String = "Test") = Password(
        id = 0,
        username = "user",
        domainInfos = emptySet(),
        score = Password.Score.Strong,
        totpSecret = null,
        vaultItemId = 0,
        name = name,
        encryptedData = SecretData.EMPTY_STRING,
        note = null
    )

    @Test
    fun `delegates password to passwordRepository`() = runTest {
        val password = testPassword()
        coEvery { passwordRepository.createOrUpdatePassword(password) } returns Result.Success(1L)

        useCase(password)

        coVerify { passwordRepository.createOrUpdatePassword(password) }
    }

    @Test
    fun `returns success with item id`() = runTest {
        val password = testPassword()
        coEvery { passwordRepository.createOrUpdatePassword(password) } returns Result.Success(42L)

        val result = useCase(password)

        assertTrue(result.isSuccess())
        assertEquals(42L, (result as Result.Success).success)
    }

    @Test
    fun `returns failure from repository`() = runTest {
        val password = testPassword()
        val error = RuntimeException("db error")
        coEvery { passwordRepository.createOrUpdatePassword(password) } returns Result.Failure(error)

        val result = useCase(password)

        assertTrue(result.isFailure())
        assertEquals(error, (result as Result.Failure).error)
    }
}

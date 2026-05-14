package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpsertItemUseCaseTest {

    private val loginRepository = FakeLoginRepository()
    private val useCase = UpsertVaultItemUseCase(loginRepository)

    private fun testLogin(name: String = "Test") = Login(
        id = newItemId(),
        username = "user",
        domainInfos = emptySet(),
        passwordCredential = PasswordCredential( // TODO(#43-task3)
            secret = PasswordSecret(EncryptedPayload.EMPTY),
            score = PasswordScore.Strong,
        ),
        totp = null,
        name = name,
        note = null,
        pinned = false,
        vaultId = newVaultId(),
        keyInformation = KeyInformation(
            wrappedKey = byteArrayOf(),
            keyNonce = byteArrayOf(),
        ),
    )

    @Test
    fun `delegates login to loginRepository`() = runTest {
        val login = testLogin()

        useCase(login)

        assertNotNull(loginRepository.getLoginById(login.id))
    }

    @Test
    fun `returns success with item id`() = runTest {
        val login = testLogin()

        val result = useCase(login)

        assertTrue(result.isSuccess())
        assertEquals(login.id, result.success)
    }

    @Test
    fun `returns failure from repository`() = runTest {
        val error = RuntimeException("db error")
        loginRepository.createOrUpdateError = error

        val result = useCase(testLogin())

        assertTrue(result.isFailure())
        assertEquals(error, result.error)
    }
}

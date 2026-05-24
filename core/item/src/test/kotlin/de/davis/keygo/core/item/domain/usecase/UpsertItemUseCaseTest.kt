package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpsertItemUseCaseTest {

    private val loginRepository = FakeLoginRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val useCase = UpsertVaultItemUseCase(loginRepository, creditCardRepository)

    private fun testLogin(name: String = "Test") = Login(
        id = newItemId(),
        username = "user",
        domainInfos = emptySet(),
        passwordCredential = PasswordCredential(
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

    private fun testCreditCard(name: String = "Test Card") = CreditCard(
        id = newItemId(),
        vaultId = newVaultId(),
        name = name,
        keyInformation = KeyInformation(
            wrappedKey = byteArrayOf(),
            keyNonce = byteArrayOf(),
        ),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = "Alice",
        lastNumbers = "4242",
        cardNumber = CreditCard.CardNumber(EncryptedPayload.EMPTY),
        cvv = CreditCard.CVV(EncryptedPayload.EMPTY),
        expirationDate = YearMonth.of(2030, 12),
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

    @Test
    fun `delegates credit card to creditCardRepository`() = runTest {
        val card = testCreditCard()

        useCase(card)

        assertNotNull(creditCardRepository.getCreditCardById(card.id))
    }

    @Test
    fun `returns success with credit card id`() = runTest {
        val card = testCreditCard()

        val result = useCase(card)

        assertTrue(result.isSuccess())
        assertEquals(card.id, result.success)
    }

    @Test
    fun `returns failure from creditCardRepository`() = runTest {
        val error = RuntimeException("db error")
        creditCardRepository.createOrUpdateError = error

        val result = useCase(testCreditCard())

        assertTrue(result.isFailure())
        assertEquals(error, result.error)
    }
}

package de.davis.keygo.core.security.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ItemWithCryptoScopeUseCaseTest {

    private val vaultId = newVaultId()
    private val defaultVault = Vault(
        id = vaultId,
        name = "Default vault",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    private val vaultRepository = FakeVaultRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val cryptoProvider = FakeCryptographicScopeProvider(FakeItemRepository())
    private val useCase = ItemWithCryptoScopeUseCase(vaultRepository, cryptoProvider)

    private fun card(id: ItemId) = CreditCard(
        id = id,
        vaultId = vaultId,
        name = "Test Card",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = "Alice",
        lastNumbers = "4242",
        cardNumber = CreditCard.CardNumber(EncryptedPayload.EMPTY),
        cvv = CreditCard.CVV(EncryptedPayload.EMPTY),
        expirationDate = YearMonth.of(2030, 12),
    )

    @BeforeTest
    fun setup() = runTest {
        vaultRepository.seed(defaultVault)
    }

    @Test
    fun `oneShot runs block with fetched item and returns success`() = runTest {
        val id = newItemId()
        creditCardRepository.seed(card(id))

        val result = useCase.oneShot(
            itemId = id,
            fetch = creditCardRepository::getCreditCardById,
        ) { it.name }

        assertTrue(result.isSuccess())
        assertEquals("Test Card", result.getOrNull())
    }

    @Test
    fun `oneShot returns IdNotFound when item is missing`() = runTest {
        val result = useCase.oneShot(
            itemId = newItemId(),
            fetch = creditCardRepository::getCreditCardById,
        ) { it.name }

        assertTrue(result.isFailure())
        val failure = assertIs<Result.Failure<*, *>>(result)
        assertIs<CryptoScopeError.IdNotFound>(failure.error)
    }

    @Test
    fun `oneShot returns IdNotFound when vault key is missing`() = runTest {
        val id = newItemId()
        creditCardRepository.seed(card(id).copy(vaultId = newVaultId()))

        val result = useCase.oneShot(
            itemId = id,
            fetch = creditCardRepository::getCreditCardById,
        ) { it.name }

        assertTrue(result.isFailure())
        val failure = assertIs<Result.Failure<*, *>>(result)
        assertIs<CryptoScopeError.IdNotFound>(failure.error)
    }

    @Test
    fun `observe emits success carrying the block result`() = runTest {
        val id = newItemId()
        creditCardRepository.seed(card(id))

        val result = useCase.observe(
            itemId = id,
            source = creditCardRepository::observeCreditCardById,
        ) { it.name }.first()

        assertTrue(result.isSuccess())
        assertEquals("Test Card", result.getOrNull())
    }

    @Test
    fun `observe emits IdNotFound when item is missing`() = runTest {
        val result = useCase.observe(
            itemId = newItemId(),
            source = creditCardRepository::observeCreditCardById,
        ) { it.name }.first()

        assertTrue(result.isFailure())
        val failure = assertIs<Result.Failure<*, *>>(result)
        assertIs<CryptoScopeError.IdNotFound>(failure.error)
    }
}

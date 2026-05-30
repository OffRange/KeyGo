package de.davis.keygo.feature.item.view.creditcard

import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.usecase.ItemWithCryptoScopeUseCase
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateCreditCardUseCase
import de.davis.keygo.feature.item.view.login.model.ObfuscatedString
import de.davis.keygo.rust.FakeCardFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewCreditCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val vaultId = newVaultId()
    private val itemId = newItemId()

    private val vault = Vault(
        id = vaultId,
        name = "Test Vault",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    // Encrypt "4111111111111111" using the fake's XOR transform so decrypt produces the original
    private val encryptedCardNumber = CreditCard.CardNumber(
        payload = EncryptedPayload(
            ciphertext = FakeCryptographicScopeProvider.transform(
                "4111111111111111".encodeToByteArray()
            ),
            iv = FakeCryptographicScopeProvider.IV,
        )
    )

    private val creditCard = CreditCard(
        id = itemId,
        vaultId = vaultId,
        name = "Test Card",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = null,
        cardNumber = encryptedCardNumber,
        cvv = null,
        expirationDate = null,
    )

    private val vaultRepository = FakeVaultRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val itemRepository = FakeItemRepository()
    private val cryptoProvider = FakeCryptographicScopeProvider(itemRepository)
    private val cardFormatter = FakeCardFormatter().apply {
        formatResult = { input -> input.chunked(4).joinToString(" ") }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vaultRepository.seed(vault)
        creditCardRepository.seed(creditCard)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `card number formatted according to card network`() = runTest(dispatcher) {
        val cardNumber = awaitCardNumber()
        assertEquals("4111 1111 1111 1111", cardNumber.formatted)
    }

    @Test
    fun `card number raw is unformatted digits`() = runTest(dispatcher) {
        val cardNumber = awaitCardNumber()
        assertEquals("4111111111111111", cardNumber.raw)
    }

    @Test
    fun `card number hidden reveals only the last four digits`() = runTest(dispatcher) {
        val cardNumber = awaitCardNumber()
        assertEquals("•••• •••• •••• 1111", cardNumber.hidden)
    }

    private suspend fun awaitCardNumber(): ObfuscatedString {
        val vm = makeViewModel().also { it.init(itemId) }
        try {
            val state = vm.state.first { it.cardNumber != null }
            return state.cardNumber!!
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun makeViewModel(): ViewCreditCardViewModel {
        val sort = SortUseCase()
        val observeAllTags = ObserveAllTagsSortedUseCase(itemRepository, sort)
        val cryptoScopeUseCase = ItemWithCryptoScopeUseCase(vaultRepository, cryptoProvider)
        val upsertVaultItem = UpsertVaultItemUseCase(FakeLoginRepository(), creditCardRepository)
        val updateCreditCard = CreateNewOrUpdateCreditCardUseCase(
            creditCardRepository = creditCardRepository,
            cardFormatter = cardFormatter,
            cryptographicScopeProvider = cryptoProvider,
            vaultRepository = vaultRepository,
            upsertVaultItem = upsertVaultItem,
        )
        return ViewCreditCardViewModel(
            itemRepository = itemRepository,
            vaultRepository = vaultRepository,
            creditCardRepository = creditCardRepository,
            updateCreditCard = updateCreditCard,
            observeCreditCardWithCryptoScope = cryptoScopeUseCase,
            observeAllTags = observeAllTags,
            sort = sort,
            cardFormatter = cardFormatter,
        )
    }
}

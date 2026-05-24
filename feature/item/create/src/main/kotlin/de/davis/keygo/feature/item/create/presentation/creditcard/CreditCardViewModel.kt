package de.davis.keygo.feature.item.create.presentation.creditcard

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.usecase.ItemWithCryptoScopeUseCase
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.create.presentation.ItemViewModel
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardBaseState
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardUiEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.time.format.DateTimeFormatter

@KoinViewModel
internal class CreditCardViewModel(
    private val itemWithCryptoScope: ItemWithCryptoScopeUseCase,
    private val creditCardRepository: CreditCardRepository,
    vaultContextRepository: VaultContextRepository,
    itemRepository: ItemRepository,
    observeAllTags: ObserveAllTagsSortedUseCase,
    vaultRepository: VaultRepository,
) : ItemViewModel<CreditCardBaseState>(
    vaultContextRepository = vaultContextRepository,
    itemRepository = itemRepository,
    observeAllTags = observeAllTags,
    vaultRepository = vaultRepository,
) {

    private val _base = MutableStateFlow(CreditCardBaseState())

    override val itemState: Flow<CreditCardBaseState> = _base

    fun init(information: DetailPaneInformation) {
        when (information) {
            is DetailPaneInformation.Init.Existing ->
                viewModelScope.launch { initWithId(information.id) }

            is DetailPaneInformation.Init.New,
            is DetailPaneInformation.Init.TOTP,
            is DetailPaneInformation.CreateRaw -> Unit // nothing to prefill
        }
    }

    private suspend fun initWithId(itemId: ItemId) {
        this.itemId = itemId

        itemWithCryptoScope.oneShot(
            itemId = itemId,
            fetch = creditCardRepository::getCreditCardById,
        ) { card ->
            val (number, cvv) = coroutineScope {
                val number = async { card.cardNumber.decrypt() }
                val cvv = card.cvv?.let { secret -> async { secret.decrypt() } }
                number.await() to cvv?.await()
            }

            nameTextFieldState.setTextAndPlaceCursorAtEnd(card.name)
            notesTextFieldState.setTextAndPlaceCursorAtEnd(card.note ?: "")
            setSelectedVaultId(card.vaultId)
            setAssignedTags(card.tags)
            _base.update {
                it.copy(
                    ccHolderTextFieldState = TextFieldState(card.holder ?: ""),
                    ccNumberTextFieldState = TextFieldState(number),
                    ccCVVTextFieldState = TextFieldState(cvv ?: ""),
                    ccExpirationDateTextFieldState = TextFieldState(
                        card.expirationDate.format(EXPIRATION_FORMATTER),
                    ),
                    updating = true,
                )
            }
        }
    }

    override fun onSubmit() {
        // TODO: persist the credit card once a CreateNewOrUpdateCreditCard use case exists.
        //  Building + encrypting the CreditCard here would put crypto/business logic in the
        //  ViewModel; the architecture keeps that in a use case (see CreateNewOrUpdateLoginUseCase).
    }

    fun onEvent(event: CreditCardUiEvent) {
        when (event) {
            is CreditCardUiEvent.ItemUi -> onItemUiEvent(event.event)
        }
    }

    companion object {
        private val EXPIRATION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")
    }
}

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
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.item.core.domain.model.CreditCardUpsertError
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertCreditCard
import de.davis.keygo.feature.item.core.domain.model.fieldUpdate
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateCreditCardUseCase
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.ItemViewModel
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardBaseState
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardUiEvent
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CreditCardViewModel(
    private val itemWithCryptoScope: ItemWithCryptoScopeUseCase,
    private val creditCardRepository: CreditCardRepository,
    private val createNewOrUpdateCreditCard: CreateNewOrUpdateCreditCardUseCase,
    private val snackbarManager: SnackbarManager,
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

    private val ccHolderTextFieldState = TextFieldState()
    private val ccNumberTextFieldState = TextFieldState()
    private val ccCVVTextFieldState = TextFieldState()
    private val ccExpirationDateTextFieldState = TextFieldState()

    private val _base = MutableStateFlow(
        CreditCardBaseState(
            ccHolderTextFieldState = ccHolderTextFieldState,
            ccNumberTextFieldState = ccNumberTextFieldState,
            ccCVVTextFieldState = ccCVVTextFieldState,
            ccExpirationDateTextFieldState = ccExpirationDateTextFieldState,
        ),
    )

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
                val number = card.cardNumber?.let { number -> async { number.decrypt() } }
                val cvv = card.cvv?.let { secret -> async { secret.decrypt() } }
                number?.await() to cvv?.await()
            }

            nameTextFieldState.setTextAndPlaceCursorAtEnd(card.name)
            notesTextFieldState.setTextAndPlaceCursorAtEnd(card.note ?: "")
            ccHolderTextFieldState.setTextAndPlaceCursorAtEnd(card.holder ?: "")
            // Set the number before the CVV so the network (and thus the CVV cap) is known.
            ccNumberTextFieldState.setTextAndPlaceCursorAtEnd(number ?: "")
            ccCVVTextFieldState.setTextAndPlaceCursorAtEnd(cvv ?: "")
            ccExpirationDateTextFieldState.setTextAndPlaceCursorAtEnd(
                card.expirationDate?.format(CC_EXPIRATION_FORMATTER) ?: "",
            )
            setSelectedVaultId(card.vaultId)
            setAssignedTags(card.tags)
            _base.update { it.copy(updating = true) }
        }
    }

    override fun onSubmit() {
        val ready = state.value as? ItemUiState.Ready ?: return
        val assignedTags = ready.shared.itemAssignedTags
        val selectedVaultId = ready.shared.vaultsState.selectedVaultId
        viewModelScope.launch {
            val upsert = itemId?.let { id ->
                UpsertCreditCard.update(
                    itemId = id,
                    vaultId = selectedVaultId,
                    name = fieldUpdate(nameTextFieldState.text.toString()),
                    cardNumber = fieldUpdate(ccNumberTextFieldState.text.toString()),
                    cvv = fieldUpdate(ccCVVTextFieldState.text.toString()),
                    expirationDate = fieldUpdate(ccExpirationDateTextFieldState.text.toString()),
                    holder = fieldUpdate(ccHolderTextFieldState.text.toString()),
                    note = fieldUpdate(notesTextFieldState.text.toString()),
                    tags = set(assignedTags),
                )
            } ?: UpsertCreditCard.create(
                vaultId = selectedVaultId,
                name = nameTextFieldState.text.toString(),
                cardNumber = ccNumberTextFieldState.text.toString(),
                expirationDate = ccExpirationDateTextFieldState.text.toString(),
                holder = ccHolderTextFieldState.text.toString(),
                cvv = ccCVVTextFieldState.text.toString(),
                note = notesTextFieldState.text.toString(),
                tags = assignedTags,
            )

            createNewOrUpdateCreditCard(upsert).onSuccess {
                navigateUp(it)
            }.onFailure { failure ->
                _base.update {
                    it.copy(
                        numberError = if (failure.contains(CreditCardUpsertError.InvalidCardNumber)) InputFieldError.Invalid else null,
                        cvvError = if (failure.contains(CreditCardUpsertError.InvalidCvv)) InputFieldError.Invalid else null,
                        expirationDateError = if (failure.contains(CreditCardUpsertError.InvalidExpiration)) InputFieldError.Invalid else null,
                    )
                }

                if (failure.any { it is ItemUpsertError.InvalidVaultId }) {
                    snackbarManager.sendMessage(
                        message = SnackbarMessage(
                            message = ResourceString(R.string.invalid_vault_id),
                        ),
                    )
                }

                failure.filterIsInstance<ItemUpsertError.DatabaseError>()
                    .firstOrNull()
                    ?.let { dbError ->
                        snackbarManager.sendMessage(
                            message = SnackbarMessage(
                                message = ResourceString(
                                    R.string.database_error,
                                    dbError.throwable.message ?: "no message",
                                ),
                            ),
                        )
                    }
            }
        }
    }

    fun onEvent(event: CreditCardUiEvent) {
        when (event) {
            is CreditCardUiEvent.ItemUi -> onItemUiEvent(event.event)
            is CreditCardUiEvent.OnCardScanned -> applyScannedCard(event.card)
        }
    }

    private fun applyScannedCard(card: Card) {
        val fields = card.toScannedFields()
        ccNumberTextFieldState.setTextAndPlaceCursorAtEnd(fields.number)
        ccExpirationDateTextFieldState.setTextAndPlaceCursorAtEnd(fields.expiry)
        fields.holder?.let(ccHolderTextFieldState::setTextAndPlaceCursorAtEnd)
    }
}

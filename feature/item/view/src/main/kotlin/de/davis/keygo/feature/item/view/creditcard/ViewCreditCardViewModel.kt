package de.davis.keygo.feature.item.view.creditcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.usecase.ItemWithCryptoScopeUseCase
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.item.core.domain.model.CreditCardUpsertError
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertCreditCard
import de.davis.keygo.feature.item.core.domain.model.fieldUpdate
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateCreditCardUseCase
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.view.creditcard.model.CreditCardFieldType
import de.davis.keygo.feature.item.view.creditcard.model.ModificationDialog
import de.davis.keygo.feature.item.view.creditcard.model.ViewCreditCardState
import de.davis.keygo.feature.item.view.creditcard.model.ViewCreditCardUiEvent
import de.davis.keygo.feature.item.view.login.model.ObfuscatedString
import de.davis.keygo.feature.item.view.login.model.asObfuscatedString
import de.davis.keygo.rust.card.CardFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.time.format.DateTimeFormatter

@KoinViewModel
internal class ViewCreditCardViewModel(
    private val itemRepository: ItemRepository,
    private val vaultRepository: VaultRepository,
    private val creditCardRepository: CreditCardRepository,
    private val updateCreditCard: CreateNewOrUpdateCreditCardUseCase,
    private val observeCreditCardWithCryptoScope: ItemWithCryptoScopeUseCase,
    private val observeAllTags: ObserveAllTagsSortedUseCase,
    private val sort: SortUseCase,
    private val cardFormatter: CardFormatter,
) : ViewModel() {

    private val _modificationDialogState = MutableStateFlow<ModificationDialog?>(null)
    private val _itemId = MutableStateFlow<ItemId?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _stateWithoutModification: Flow<ViewCreditCardState> = _itemId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { id ->
            observeCreditCardWithCryptoScope.observe(
                itemId = id,
                source = creditCardRepository::observeCreditCardById,
            ) { card ->
                val (number, cvv, vaultMetadata) = coroutineScope {
                    val number = card.cardNumber?.let {
                        async {
                            val raw = it.decrypt()
                            ObfuscatedString(
                                raw = raw,
                                formatted = cardFormatter.formatNumber(raw),
                                visibleSuffixDigits = VISIBLE_CARD_NUMBER_SUFFIX,
                            )
                        }
                    }
                    val cvv = card.cvv?.let { async { it.decrypt().asObfuscatedString() } }
                    val meta = async { vaultRepository.getVaultMetadata(card.vaultId) }
                    Triple(number?.await(), cvv?.await(), meta.await())
                }

                ViewCreditCardState(
                    name = card.name,
                    vaultMetadata = vaultMetadata,
                    holder = card.holder.orEmpty(),
                    cardNumber = number,
                    lastNumbers = card.lastNumbers.orEmpty(),
                    cvv = cvv,
                    expirationDate = card.expirationDate?.format(EXPIRATION_FORMATTER).orEmpty(),
                    tags = sort(card.tags) { it.display }.toSet(),
                    note = card.note.orEmpty(),
                    pinned = card.pinned,
                )
            }
                .map { it.getOrNull() }
                .filterNotNull()
        }.flowOn(Dispatchers.Default)

    val state = combine(
        _stateWithoutModification,
        _modificationDialogState,
    ) { base, modificationDialog ->
        base.copy(modificationDialog = modificationDialog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ViewCreditCardState(),
    )

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    fun init(itemId: ItemId) {
        _itemId.update { itemId }
    }

    fun onEvent(event: ViewCreditCardUiEvent) {
        when (event) {
            ViewCreditCardUiEvent.OnBackClick -> viewModelScope.launch {
                navigationEventChannel.send(NavigationEvent.NavigateBack)
            }

            ViewCreditCardUiEvent.OnPinClick -> _itemId.value?.let { id ->
                viewModelScope.launch {
                    itemRepository.setPinned(id, !state.value.pinned)
                }
            }

            ViewCreditCardUiEvent.OnEditRequest -> _itemId.value?.let { id ->
                viewModelScope.launch {
                    navigationEventChannel.send(
                        NavigationEvent.NavigateToEdit(VaultItemType.CreditCard, id),
                    )
                }
            }

            ViewCreditCardUiEvent.OnCloseDialog -> _modificationDialogState.update { null }

            is ViewCreditCardUiEvent.OnModifyFieldRequest -> viewModelScope.launch {
                val fieldType = event.fieldType
                val current = state.value
                val initialValue = when (fieldType) {
                    CreditCardFieldType.Holder -> current.holder
                    CreditCardFieldType.CardNumber -> current.cardNumber?.raw.orEmpty()
                    CreditCardFieldType.Cvv -> current.cvv?.raw.orEmpty()
                    CreditCardFieldType.Expiration -> current.expirationDate
                    CreditCardFieldType.Note -> current.note
                    CreditCardFieldType.Tag -> ""
                }
                val tagsToSuggest =
                    if (fieldType == CreditCardFieldType.Tag) observeAllTags().first()
                        .toSet() - current.tags
                    else emptySet()

                _modificationDialogState.update {
                    ModificationDialog(
                        fieldType = fieldType,
                        initialValue = initialValue,
                        tagsToSuggest = tagsToSuggest,
                    )
                }
            }

            is ViewCreditCardUiEvent.OnSubmitModification -> {
                val dialog = _modificationDialogState.value ?: return
                val newText = fieldUpdate(event.input)

                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        updateCreditCard(
                            when (dialog.fieldType) {
                                CreditCardFieldType.Holder -> UpsertCreditCard.update(
                                    itemId = id,
                                    holder = newText,
                                )

                                CreditCardFieldType.CardNumber -> UpsertCreditCard.update(
                                    itemId = id,
                                    cardNumber = newText,
                                )

                                CreditCardFieldType.Cvv -> UpsertCreditCard.update(
                                    itemId = id,
                                    cvv = newText,
                                )

                                CreditCardFieldType.Expiration -> UpsertCreditCard.update(
                                    itemId = id,
                                    expirationDate = newText,
                                )

                                CreditCardFieldType.Note -> UpsertCreditCard.update(
                                    itemId = id,
                                    note = newText,
                                )

                                CreditCardFieldType.Tag -> newText.onSet { raw ->
                                    Tag.of(raw)?.let { tag ->
                                        UpsertCreditCard.update(
                                            itemId = id,
                                            tags = set(state.value.tags + tag),
                                        )
                                    }
                                } ?: return@launch
                            },
                        ).onFailure { failure ->
                            _modificationDialogState.update {
                                dialog.copy(
                                    error = when {
                                        failure.contains(CreditCardUpsertError.InvalidCardNumber) ->
                                            InputFieldError.Invalid

                                        failure.contains(CreditCardUpsertError.InvalidExpiration) ->
                                            InputFieldError.Invalid

                                        failure.contains(ItemUpsertError.BlankName) ->
                                            InputFieldError.Empty

                                        failure.contains(ItemUpsertError.Empty) ->
                                            InputFieldError.Empty

                                        else -> null
                                    },
                                )
                            }
                        }.onSuccess {
                            _modificationDialogState.update { null }
                        }
                    }
                }
            }
        }
    }

    companion object {
        // "yy" parses into the 2000-2099 range, matching card expirations.
        private val EXPIRATION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yy")
        private const val VISIBLE_CARD_NUMBER_SUFFIX = 4
    }
}

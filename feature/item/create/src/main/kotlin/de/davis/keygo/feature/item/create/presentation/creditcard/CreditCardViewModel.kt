package de.davis.keygo.feature.item.create.presentation.creditcard

import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.create.presentation.ItemViewModel
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardBaseState
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardUiEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CreditCardViewModel(
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
            // TODO: load + decrypt the existing card into _base once a read/decrypt use case
            //  exists. Decryption requires a cryptographic scope, which lives in a use case.
            is DetailPaneInformation.Init.Existing -> itemId = information.id

            is DetailPaneInformation.Init.New,
            is DetailPaneInformation.Init.TOTP,
            is DetailPaneInformation.CreateRaw -> Unit // nothing to prefill
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
}

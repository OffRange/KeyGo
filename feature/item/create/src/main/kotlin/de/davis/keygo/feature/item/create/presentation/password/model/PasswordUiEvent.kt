package de.davis.keygo.feature.item.create.presentation.password.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.feature.item.core.presentation.password.model.FieldType

sealed interface PasswordUiEvent {
    data object OnSubmit : PasswordUiEvent
    data object OnGeneratePasswordClick : PasswordUiEvent
    data object OnBackClick : PasswordUiEvent
    data object OnCloseBottomSheet : PasswordUiEvent
    data object OnScanCodeRequest : PasswordUiEvent

    data class OnDeleteDomain(val value: String) : PasswordUiEvent
    data class OnAddDomains(val domains: Set<String>) : PasswordUiEvent

    data object OnTotpParseErrorDismiss : PasswordUiEvent

    data class OnCodesScanned(val codes: List<String>) : PasswordUiEvent
    data class OnTotpModificationItemSelected(val itemId: ItemId) : PasswordUiEvent
    data object OnCreateNewItemForTotp : PasswordUiEvent

    data class OnOverrideFieldClicked(val fieldType: FieldType) : PasswordUiEvent
    data object OnOverrideTotpFieldsConfirmed : PasswordUiEvent
    data object OnOverrideTotpFieldsKept : PasswordUiEvent
}
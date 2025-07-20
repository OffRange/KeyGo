package de.davis.keygo.item.create.presentation.password.model

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.item.core.presentation.password.model.FieldType

sealed interface PasswordUiEvent {
    data object OnSubmit : PasswordUiEvent
    data object OnGeneratePasswordClick : PasswordUiEvent
    data object OnBackClick : PasswordUiEvent
    data object OnCloseBottomSheet : PasswordUiEvent
    data object OnScanCodeRequest : PasswordUiEvent

    data object OnTotpParseErrorDismiss : PasswordUiEvent

    data class OnCodesScanned(val codes: List<String>) : PasswordUiEvent
    data class OnTotpModificationItemSelected(val itemId: ItemId) : PasswordUiEvent
    data object OnCreateNewItemForTotp : PasswordUiEvent

    data class OnOverrideFieldClicked(val fieldType: FieldType) : PasswordUiEvent
    data object OnOverrideTotpFieldsConfirmed : PasswordUiEvent
    data object OnOverrideTotpFieldsKept : PasswordUiEvent
}
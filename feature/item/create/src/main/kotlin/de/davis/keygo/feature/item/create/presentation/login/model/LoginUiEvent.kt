package de.davis.keygo.feature.item.create.presentation.login.model

import de.davis.keygo.core.item.domain.model.PasskeyRef
import de.davis.keygo.feature.item.core.presentation.login.model.FieldType
import de.davis.keygo.feature.item.create.presentation.model.ItemUiEvent

internal sealed interface LoginUiEvent {
    data class ItemUi(val event: ItemUiEvent) : LoginUiEvent

    data object OnGeneratePasswordClick : LoginUiEvent
    data object OnCloseBottomSheet : LoginUiEvent
    data object OnScanCodeRequest : LoginUiEvent

    data class OnDeletePasskeyRequest(val passkey: PasskeyRef) : LoginUiEvent
    data object OnConfirmPasskeyDeletion : LoginUiEvent
    data object OnPasskeyDeletionDismiss : LoginUiEvent

    data class OnDeleteDomain(val value: String) : LoginUiEvent
    data class OnAddDomains(val domains: Set<String>) : LoginUiEvent

    data object OnTotpParseErrorDismiss : LoginUiEvent

    data class OnCodesScanned(val codes: List<String>) : LoginUiEvent

    data class OnOverrideFieldClicked(val fieldType: FieldType) : LoginUiEvent
    data object OnOverrideTotpFieldsConfirmed : LoginUiEvent
    data object OnOverrideTotpFieldsKept : LoginUiEvent

    data class OnPasswordGenerated(val password: String) : LoginUiEvent
}

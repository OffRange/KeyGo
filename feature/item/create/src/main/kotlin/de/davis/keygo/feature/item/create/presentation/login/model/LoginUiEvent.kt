package de.davis.keygo.feature.item.create.presentation.login.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.feature.item.core.presentation.login.model.FieldType

internal sealed interface LoginUiEvent {
    data object OnSubmit : LoginUiEvent
    data object OnGeneratePasswordClick : LoginUiEvent
    data object OnBackClick : LoginUiEvent
    data object OnCloseBottomSheet : LoginUiEvent
    data object OnScanCodeRequest : LoginUiEvent

    data class OnDeleteDomain(val value: String) : LoginUiEvent
    data class OnAddDomains(val domains: Set<String>) : LoginUiEvent

    data object OnTotpParseErrorDismiss : LoginUiEvent

    data class OnCodesScanned(val codes: List<String>) : LoginUiEvent
    data class OnTotpModificationItemSelected(val itemId: ItemId) : LoginUiEvent
    data object OnCreateNewItemForTotp : LoginUiEvent

    data class OnOverrideFieldClicked(val fieldType: FieldType) : LoginUiEvent
    data object OnOverrideTotpFieldsConfirmed : LoginUiEvent
    data object OnOverrideTotpFieldsKept : LoginUiEvent

    data class OnPasswordGenerated(val password: String) : LoginUiEvent

    data class OnVaultSelected(val vaultId: VaultId) : LoginUiEvent
}

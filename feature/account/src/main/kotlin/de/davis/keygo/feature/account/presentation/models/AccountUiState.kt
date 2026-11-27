package de.davis.keygo.feature.account.presentation.models

import de.davis.keygo.core.sync.domain.model.ServerLink

internal data class AccountUiState(
    val link: ServerLink = ServerLink.None,
    val logoutDialog: LogoutDialogState = LogoutDialogState.Hidden,
    val devices: DevicesSummary = DevicesSummary.Loading,
    val deleteAccountDialog: DeleteAccountDialogState = DeleteAccountDialogState.Hidden,
)

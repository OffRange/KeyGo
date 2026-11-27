package de.davis.keygo.feature.account.presentation.models

import de.davis.keygo.core.sync.domain.model.SyncError

internal sealed interface DeleteAccountDialogState {

    data object Hidden : DeleteAccountDialogState
    data class Confirming(val error: SyncError? = null) : DeleteAccountDialogState
    data object Deleting : DeleteAccountDialogState
}

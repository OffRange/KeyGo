package de.davis.keygo.feature.vault.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.vault.presentation.components.MoveVaultDialog
import de.davis.keygo.feature.vault.presentation.components.VaultCreationDialog
import de.davis.keygo.feature.vault.presentation.components.VaultDeletionDialog
import de.davis.keygo.feature.vault.presentation.components.VaultSelectionSheet
import de.davis.keygo.feature.vault.presentation.model.VaultState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFlow(onDismiss: () -> Unit) {
    val vm = koinViewModel<VaultFlowViewModel>()

    ObserveAsEvents(vm.dismissEvents) { onDismiss() }

    val vaultState by vm.vaultState.collectAsStateWithLifecycle()

    when (val state = vaultState) {
        is VaultState.Delete -> VaultDeletionDialog(
            vaultState = state,
            onConfirmDeletion = vm::onDeleteVault,
            onDismissRequest = vm::dismiss,
        )

        is VaultState.Select -> VaultSelectionSheet(
            vaultState = state,
            onDismiss = vm::dismiss,
            onVaultContextSelect = vm::onVaultContextSelected,
            onCreateVaultRequest = vm::onCreateVaultRequest,
            onEditRequest = vm::onEditVaultRequest,
            onDeleteRequest = vm::onDeleteRequest,
            onMoveTo = vm::onMoveTo,
        )

        is VaultState.CreateOrUpdate -> VaultCreationDialog(
            vaultState = state,
            onDismissRequest = vm::dismiss,
            onCreateOrEdit = vm::onCreateOrEditVault,
            onIconClick = vm::onVaultIconClick,
        )

        is VaultState.Move -> MoveVaultDialog(
            vaultState = state,
            onDismissRequest = vm::dismiss,
            onDstSelected = vm::onMoveDstSelected,
            onConfirm = vm::onConfirmMove,
            onDeleteVaultStateChange = vm::onMoveDeleteVaultStateChange,
        )
    }
}

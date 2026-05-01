package de.davis.keygo.feature.vault.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.feature.vault.presentation.components.MoveVaultDialog
import de.davis.keygo.feature.vault.presentation.components.VaultCreationDialog
import de.davis.keygo.feature.vault.presentation.components.VaultSelectionSheet
import de.davis.keygo.feature.vault.presentation.model.VaultState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFlow(onDismiss: () -> Unit) {
    val viewModelStore = remember { ViewModelStore() }
    DisposableEffect(Unit) {
        onDispose { viewModelStore.clear() }
    }
    val viewModelStoreOwner = remember(viewModelStore) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = viewModelStore
        }
    }
    val vm = koinViewModel<VaultFlowViewModel>(viewModelStoreOwner = viewModelStoreOwner)

    val vaultState by vm.vaultState.collectAsStateWithLifecycle()

    LaunchedEffect(vaultState) {
        if (vaultState == VaultState.Closed) onDismiss()
    }

    when (val state = vaultState) {
        VaultState.Closed -> {}
        is VaultState.Select -> VaultSelectionSheet(
            vaultState = state,
            onDismiss = vm::dismiss,
            onVaultContextSelect = vm::onVaultContextSelected,
            onCreateVaultRequest = vm::onCreateVaultRequest,
            onEditRequest = vm::onEditVaultRequest,
            onDelete = vm::onDeleteVault,
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
        )
    }
}

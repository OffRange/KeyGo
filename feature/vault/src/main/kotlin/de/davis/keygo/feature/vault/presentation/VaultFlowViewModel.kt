package de.davis.keygo.feature.vault.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.vault.domain.model.MoveItemsProgress
import de.davis.keygo.feature.vault.domain.usecase.CreateVaultUseCase
import de.davis.keygo.feature.vault.domain.usecase.DeleteVaultUseCase
import de.davis.keygo.feature.vault.domain.usecase.EditVaultUseCase
import de.davis.keygo.feature.vault.domain.usecase.MoveItemsToVaultUseCase
import de.davis.keygo.feature.vault.domain.usecase.ObserveVaultsAndSelectionUseCase
import de.davis.keygo.feature.vault.domain.usecase.SetVaultContextUseCase
import de.davis.keygo.feature.vault.presentation.model.VaultDeletionError
import de.davis.keygo.feature.vault.presentation.model.VaultState
import de.davis.keygo.feature.vault.presentation.model.VaultStateSwitcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class VaultFlowViewModel(
    private val setVaultContextUseCase: SetVaultContextUseCase,
    private val createVault: CreateVaultUseCase,
    private val updateVault: EditVaultUseCase,
    private val deleteVault: DeleteVaultUseCase,
    private val moveItemsToVault: MoveItemsToVaultUseCase,
    observeVaultsAndSelection: ObserveVaultsAndSelectionUseCase,
) : ViewModel() {

    private val vaultsAndSelection = observeVaultsAndSelection()

    private val vaultStateSwitcher =
        MutableStateFlow<VaultStateSwitcher>(VaultStateSwitcher.Selection)
    private val deleteVaultState = MutableStateFlow<VaultState.Delete?>(null)
    private val createVaultState = MutableStateFlow(VaultState.Create())
    private val editVaultState = MutableStateFlow<VaultState.Edit?>(null)
    private val moveDstSelection = MutableStateFlow<VaultId?>(null)
    private val moveProgress = MutableStateFlow<MoveItemsProgress?>(null)

    private val _dismissEvents = Channel<Unit>(Channel.CONFLATED)
    val dismissEvents = _dismissEvents.receiveAsFlow()

    private val vaultSelectionState = vaultsAndSelection.map {
        VaultState.Select(vaults = it.vaults, vaultContext = it.selection)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val vaultState = vaultStateSwitcher.flatMapLatest { switcher ->
        when (switcher) {
            VaultStateSwitcher.Delete -> deleteVaultState.filterNotNull()
            VaultStateSwitcher.Selection -> vaultSelectionState
            VaultStateSwitcher.Create -> createVaultState
            VaultStateSwitcher.Edit -> editVaultState.filterNotNull()

            is VaultStateSwitcher.Move -> combine(
                vaultsAndSelection,
                moveDstSelection,
                moveProgress,
            ) { vs, dstId, progress ->
                val src = vs.vaults.firstOrNull { it.vaultId == switcher.srcVaultId }
                    ?: return@combine null
                val dstVaults = vs.vaults.filter { it.vaultId != switcher.srcVaultId }
                VaultState.Move(
                    srcVault = src,
                    dstVaults = dstVaults,
                    selectedDstVaultId = dstId ?: dstVaults.firstOrNull()?.vaultId,
                    progress = progress,
                )
            }.filterNotNull()
        }
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = VaultState.Select(),
        )

    fun onVaultContextSelected(context: VaultContext) {
        viewModelScope.launch { setVaultContextUseCase(context) }
    }

    fun onCreateVaultRequest() {
        vaultStateSwitcher.update { VaultStateSwitcher.Create }
    }

    fun onCreateOrEditVault() {
        viewModelScope.launch {
            when (vaultStateSwitcher.value) {
                VaultStateSwitcher.Delete,
                VaultStateSwitcher.Selection,
                is VaultStateSwitcher.Move -> null

                VaultStateSwitcher.Create -> {
                    val state = createVaultState.value
                    createVault(
                        name = state.nameTextFieldState.text.toString(),
                        icon = state.icon,
                    ).onFailure { error ->
                        createVaultState.update { state.copy(error = error) }
                    }
                }

                VaultStateSwitcher.Edit ->
                    editVaultState.value?.let { state ->
                        updateVault(
                            vaultId = state.vaultId,
                            name = state.nameTextFieldState.text.toString(),
                            icon = state.icon,
                        ).onFailure { error ->
                            editVaultState.update { state.copy(error = error) }
                        }
                    }
            }?.onSuccess {
                dismiss()
            }
        }
    }

    fun onVaultIconClick(icon: Vault.Icon) {
        when (vaultStateSwitcher.value) {
            VaultStateSwitcher.Delete,
            VaultStateSwitcher.Selection,
            is VaultStateSwitcher.Move -> {
            }

            VaultStateSwitcher.Create ->
                createVaultState.update { it.copy(icon = icon) }

            VaultStateSwitcher.Edit ->
                editVaultState.value?.let { state ->
                    editVaultState.update { state.copy(icon = icon) }
                }
        }
    }

    fun onEditVaultRequest(vaultMetadata: VaultMetadata) {
        editVaultState.update {
            VaultState.Edit(
                vaultId = vaultMetadata.vaultId,
                nameTextFieldState = TextFieldState(vaultMetadata.name),
                icon = vaultMetadata.icon,
            )
        }
        vaultStateSwitcher.update { VaultStateSwitcher.Edit }
    }

    fun onDeleteRequest(vaultMetadata: VaultMetadata) {
        deleteVaultState.update {
            VaultState.Delete(
                vaultMetadata = vaultMetadata
            )
        }
        vaultStateSwitcher.update { VaultStateSwitcher.Delete }
    }

    fun onDeleteVault(enteredName: String) {
        when (vaultStateSwitcher.value) {
            VaultStateSwitcher.Delete -> {
                deleteVaultState.value?.let { state ->
                    if (state.vaultMetadata.name != enteredName)
                        return@let deleteVaultState.update { state.copy(error = VaultDeletionError.NameDoesNotMatch) }

                    viewModelScope.launch { deleteVault(state.vaultMetadata.vaultId) }
                    dismiss(force = true)
                }
            }

            else -> {}
        }
    }

    fun onMoveTo(vaultId: VaultId) {
        moveDstSelection.update { null }
        moveProgress.update { null }
        vaultStateSwitcher.update { VaultStateSwitcher.Move(vaultId) }
    }

    fun onMoveDstSelected(vaultId: VaultId) {
        moveDstSelection.update { vaultId }
    }

    fun onConfirmMove() {
        val switcher = vaultStateSwitcher.value as? VaultStateSwitcher.Move ?: return
        if (moveProgress.value != null) return
        val moveState = vaultState.value as? VaultState.Move ?: return
        val dstVaultId = moveState.selectedDstVaultId ?: return
        viewModelScope.launch {
            moveItemsToVault(switcher.srcVaultId, dstVaultId) { progress ->
                moveProgress.update { progress }
            }
            dismiss(force = true)
        }
    }

    fun dismiss(force: Boolean = false) {
        if (!force && moveProgress.value != null) return
        vaultStateSwitcher.update { VaultStateSwitcher.Selection }
        createVaultState.update { VaultState.Create() }
        moveProgress.update { null }
        _dismissEvents.trySend(Unit)
    }
}

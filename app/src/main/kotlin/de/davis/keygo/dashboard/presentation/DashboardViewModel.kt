package de.davis.keygo.dashboard.presentation

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.keyGoCombine
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.core.domain.repository.VaultItemRepository
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.domain.usecase.InsertVaultItem
import de.davis.keygo.core.presentation.snackbar.ItemDeletedMessage
import de.davis.keygo.dashboard.presentation.model.DashboardNavEvent
import de.davis.keygo.dashboard.presentation.model.DashboardUIEvent
import de.davis.keygo.dashboard.presentation.model.DashboardUIState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val snackbarManager: SnackbarManager,
    private val vaultItemRepository: VaultItemRepository,
    insertVaultItem: InsertVaultItem
) : ViewModel() {

    private val textFieldState = TextFieldState()

    private val items = vaultItemRepository.observeVaultItems()

    private val searchResult = MutableStateFlow(emptyList<VaultItem>())

    private val flaggedForDeletion = MutableStateFlow(setOf<Long>())

    private val selectedItemIds = MutableStateFlow(setOf<Long>())
    private val selectionMode = selectedItemIds
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val openedItemId = MutableStateFlow(-1L)
    private val navEvent = MutableStateFlow<DashboardNavEvent>(DashboardNavEvent.None)

    init {
        viewModelScope.launch {
            items.collectLatest {
                if (it.isNotEmpty()) {
                    return@collectLatest
                }

                (0..25).map {
                    async {
                        insertVaultItem(
                            Password(
                                username = "User $it",
                                website = null,
                                name = "${if (it > 10) "A" else "B"} PWD $it",
                                encryptedData = CryptographicData.EMPTY,
                                note = null
                            )
                        )
                    }
                }.awaitAll()
            }
        }
    }

    val uiState = keyGoCombine(
        items,
        flaggedForDeletion,
        selectedItemIds,
        openedItemId,
        searchResult,
        navEvent
    ) { items, markedAsDeleted, selectedItemIds, openedItemId, _, navEvent ->
        DashboardUIState(
            textFieldState = textFieldState,
            items = items
                .filterNot { it.vaultItemId in markedAsDeleted }
                .sortedBy { it.name }
                .toImmutableList(),
            selectedItemIds = selectedItemIds.toImmutableSet(),
            openedItemId = openedItemId,
            navEvent = navEvent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUIState(textFieldState)
    )

    suspend fun runSearch() {
        snapshotFlow {
            textFieldState.text
        }.collectLatest {
            searchResult.update {
                performSearch()
            }
        }
    }

    private suspend fun performSearch(): List<VaultItem> {
        Log.d("DashboardViewModel", "performSearch: ${textFieldState.text}")
        return emptyList() // TODO("Implement search logic")
    }

    fun onEvent(event: DashboardUIEvent) = when (event) {
        is DashboardUIEvent.OnSearchSubmitted -> {

        }

        is DashboardUIEvent.OnClicked -> {
            if (selectionMode.value) {
                val id = event.vaultId
                toggleSelection(id)
            } else {
                openedItemId.update {
                    event.vaultId
                }
            }
        }

        is DashboardUIEvent.OnLongClicked -> {
            updateSelection(event.vaultId, select = true)
        }

        is DashboardUIEvent.OnDeleteRequested -> {
            val id = event.vaultId
            updateSelection(id, select = false)
            updateDeletionFlag(id, flag = true)

            viewModelScope.launch {
                snackbarManager.sendMessage(
                    ItemDeletedMessage(
                        onClick = {
                            updateDeletionFlag(id = id, flag = false)
                        },
                        onDismiss = {
                            viewModelScope.launch {
                                vaultItemRepository.deleteVaultItem(id)

                                // Inside this coroutine to ensure it only runs after the deletion
                                updateDeletionFlag(id = id, flag = false)
                            }
                        }
                    )
                )
            }
        }
    }

    private fun toggleSelection(id: Long) {
        selectedItemIds.update {
            if (id in it) it - id
            else it + id
        }
    }

    private fun updateSelection(id: Long, select: Boolean = true) {
        selectedItemIds.update {
            if (select) it + id
            else it - id
        }
    }

    private fun updateDeletionFlag(id: Long, flag: Boolean = true) {
        flaggedForDeletion.update {
            if (flag) it + id
            else it - id
        }
    }
}
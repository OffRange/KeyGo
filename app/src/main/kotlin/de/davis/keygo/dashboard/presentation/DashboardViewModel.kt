package de.davis.keygo.dashboard.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.keyGoCombine
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultSearchResult
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class DashboardViewModel(
    private val snackbarManager: SnackbarManager,
    private val vaultItemRepository: VaultItemRepository,
    insertVaultItem: InsertVaultItem
) : ViewModel() {

    private val textFieldState = TextFieldState()

    private val submittedSearchQuery = MutableStateFlow("")

    private val searchResult = MutableStateFlow(emptyList<VaultSearchResult>())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainViewItems = submittedSearchQuery.flatMapLatest { show ->
        if (show.isBlank()) {
            vaultItemRepository.observeVaultItems()
        } else {
            flowOf(searchResult.value) // TODO introduce suggestion and ful-search queries
        }
    }

    private val flaggedForDeletion = MutableStateFlow(setOf<Long>())

    private val selectedItemIds = MutableStateFlow(setOf<Long>())

    private val openedItemId = MutableStateFlow(-1L)
    private val navEvent = MutableStateFlow<DashboardNavEvent>(DashboardNavEvent.None)

    init {
        viewModelScope.launch {
            vaultItemRepository.observeVaultItems().collectLatest {
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
        mainViewItems,
        flaggedForDeletion,
        selectedItemIds,
        openedItemId,
        searchResult,
        navEvent
    ) { items, markedAsDeleted, selectedItemIds, openedItemId, searchResult, navEvent ->
        DashboardUIState(
            textFieldState = textFieldState,
            items = items
                .filterNot { it.vaultItemId in markedAsDeleted }
                .sortedBy { it.name }
                .toImmutableList(),
            searchResult = searchResult.toImmutableList(),
            selectedItemIds = selectedItemIds.toImmutableSet(),
            openedItemId = openedItemId,
            navEvent = navEvent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUIState(textFieldState)
    )

    @OptIn(FlowPreview::class)
    suspend fun runSearch() {
        snapshotFlow {
            textFieldState.text
        }.debounce(300.milliseconds)
            .distinctUntilChanged()
            .collectLatest { query ->
                searchResult.update {
                    performSearch(query.toString())
                }
            }
    }

    private suspend fun performSearch(query: String): List<VaultSearchResult> {
        return vaultItemRepository.searchVaultItem(query)
    }

    fun onEvent(event: DashboardUIEvent) {
        when (event) {
            is DashboardUIEvent.OnSearchSubmit -> {
                submittedSearchQuery.update { textFieldState.text.toString() }
            }

            is DashboardUIEvent.OnSearchClear -> {
                clearSearch()
            }

            is DashboardUIEvent.OnSearchCollapse -> {
                // Reset to the actual submitted query for cases where the user performed back press action
                textFieldState.edit {
                    replace(0, length, submittedSearchQuery.value)
                }
            }

            is DashboardUIEvent.OnOpen -> openItem(event.vaultId)

            is DashboardUIEvent.OnOpenOrSelect -> {
                if (selectedItemIds.value.isNotEmpty())
                    toggleSelection(event.vaultId)
                else
                    openItem(event.vaultId)
            }

            is DashboardUIEvent.OnLongClick -> {
                updateSelection(event.vaultId, select = true)
            }

            is DashboardUIEvent.OnDeleteRequest -> {
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
    }

    private fun openItem(id: Long) {
        openedItemId.update { id }
    }

    private fun clearSearch() {
        textFieldState.clearText()
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
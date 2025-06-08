package de.davis.keygo.dashboard.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.core.domain.repository.VaultItemRepository
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.presentation.snackbar.ItemDeletedMessage
import de.davis.keygo.dashboard.domain.model.Filter
import de.davis.keygo.dashboard.domain.usecase.FilterUseCase
import de.davis.keygo.dashboard.presentation.model.DashboardUIEvent
import de.davis.keygo.dashboard.presentation.model.DashboardUIState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
class DashboardViewModel(
    private val snackbarManager: SnackbarManager,
    private val vaultItemRepository: VaultItemRepository,
    filterItems: FilterUseCase
) : ViewModel() {

    private val textFieldState = TextFieldState()

    private val submittedSearchQuery = MutableStateFlow("")

    private val searchResult = MutableStateFlow(emptyList<VaultSearchResult>())

    private val filter = MutableStateFlow<Filter>(Filter.Alphanumerical())
    private val flaggedForDeletion = MutableStateFlow(setOf<Long>())

    private val nonDeletedSearchResult = combine(
        flaggedForDeletion,
        searchResult
    ) { flaggedForDeletion, searchResult ->
        filterItems(
            filter = Filter.Alphanumerical(),
            vaultItems = searchResult.filterNot { it.vaultItemId in flaggedForDeletion }
        )
    }


    private val repoFilteredItems = combine(
        vaultItemRepository.observeVaultItems(),
        filter,
        flaggedForDeletion
    ) { items, filter, flaggedForDeletion ->
        filterItems(
            filter = filter,
            vaultItems = items.filterNot { it.vaultItemId in flaggedForDeletion }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainViewItems = submittedSearchQuery.flatMapLatest { show ->
        if (show.isBlank()) {
            repoFilteredItems
        } else {
            nonDeletedSearchResult // TODO introduce suggestion and full-search queries
        }
    }

    private val selectedItemIds = MutableStateFlow(setOf<Long>())

    private val openedItemId = MutableStateFlow(-1L)

    val uiState = combine(
        mainViewItems,
        flaggedForDeletion,
        selectedItemIds,
        openedItemId,
        nonDeletedSearchResult,
    ) { items, markedAsDeleted, selectedItemIds, openedItemId, searchResult ->
        DashboardUIState(
            textFieldState = textFieldState,
            items = items
                .filterNot { it.vaultItemId in markedAsDeleted }
                .toImmutableList(),
            searchResult = searchResult.toImmutableList(),
            selectedItemIds = selectedItemIds.toImmutableSet(),
            openedItemId = openedItemId,
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

            is DashboardUIEvent.OnFilterChange -> {
                filter.update { event.filter }
            }
        }
    }

    private fun openItem(id: Long) {
        openedItemId.update { id }
    }

    private fun clearSearch() {
        textFieldState.clearText()
        submittedSearchQuery.update { "" }
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
package de.davis.keygo.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.repository.VaultItemRepository
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.dashboard.domain.model.Filter
import de.davis.keygo.dashboard.domain.usecase.FilterUseCase
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DashboardViewModel(
    private val snackbarManager: SnackbarManager,
    private val vaultItemRepository: VaultItemRepository,
    filterItems: FilterUseCase,
) : ViewModel() {
    private val flaggedForDeletion = MutableStateFlow(setOf<ItemId>())

    private val submittedSearchQuery = MutableStateFlow("")

    private val allItems = vaultItemRepository.observeLiteVaultItems()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemSource = submittedSearchQuery.flatMapLatest(::queryToItems)

    private val nonDeletedItems = combine(
        itemSource,
        flaggedForDeletion
    ) { items, flagged ->
        items.filterNot { item -> item.vaultItemId in flagged }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredItems = nonDeletedItems.mapLatest { items ->
        // TODO: Make filter configurable
        filterItems(Filter.Alphanumerical(), items)
    }


    private val selectedItemIds = MutableStateFlow(emptySet<ItemId>())
    private val openItemId = MutableStateFlow<ItemId?>(null)

    val listItemState = combine(
        filteredItems,
        selectedItemIds,
        openItemId
    ) { items, selectedIds, openItemId ->
        ListItemState(
            items = items,
            selectedItemIds = selectedIds,
            openedItemId = openItemId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListItemState()
    )


    private suspend fun queryToItems(query: String): Flow<List<LiteItem>> =
        if (query.isBlank()) allItems
        else flowOf(vaultItemRepository.searchVaultItem(query))

    suspend fun searcher(query: String): List<LiteItem> = queryToItems(query).first()

    fun onDelete(itemId: ItemId) {
        updateItemSelectionState(itemId, selected = false)

        updateItemDeletionState(itemId, deleted = true)
        viewModelScope.launch {
            snackbarManager.sendMessage(
                ItemDeletedMessage(
                    onClick = {
                        updateItemDeletionState(itemId, deleted = false)
                    },
                    onDismiss = {
                        viewModelScope.launch {
                            vaultItemRepository.deleteItem(itemId)

                            // Inside this coroutine to ensure it only runs after the deletion
                            updateItemDeletionState(itemId, deleted = false)
                        }
                    }
                )
            )
        }
    }

    fun onItemClick(itemId: ItemId) {
        if (selectedItemIds.value.isNotEmpty()) {
            val isSelected = itemId in selectedItemIds.value
            updateItemSelectionState(itemId, selected = !isSelected)
        } else {
            openItemId.update { itemId }
        }
    }

    fun onSearchResultClick(id: ItemId) {
        openItemId.update { id }
    }

    fun onItemLongClick(itemId: ItemId) {
        updateItemSelectionState(itemId, selected = true)
    }

    fun closeItem() {
        openItemId.update { null }
    }

    fun onSearchSubmit(query: String) {
        submittedSearchQuery.update { query }
    }


    private fun updateItemSelectionState(id: ItemId, selected: Boolean) {
        selectedItemIds.update { currentSelectedIds ->
            if (selected) currentSelectedIds + id
            else currentSelectedIds - id
        }
    }

    private fun updateItemDeletionState(id: ItemId, deleted: Boolean) {
        flaggedForDeletion.update { currentDeletedIds ->
            if (deleted) currentDeletedIds + id
            else currentDeletedIds - id
        }
    }

    override fun onCleared() {
        val pendingDeletions = flaggedForDeletion.getAndUpdate { emptySet() }
        if (pendingDeletions.isNotEmpty()) {
            runBlocking(Dispatchers.IO) {
                pendingDeletions.forEach { itemId ->
                    vaultItemRepository.deleteItem(itemId)
                }
            }
        }
    }
}
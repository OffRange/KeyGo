package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultItemRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.usecase.FilterUseCase
import de.davis.keygo.feature.list_screen.presentation.mapper.toAvailableFilterOptions
import de.davis.keygo.feature.list_screen.presentation.mapper.toBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.Event
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class ItemListViewModel(
    @InjectedParam private val enableSelection: Boolean,
    @InjectedParam private val restrictedItemType: VaultItemType?,
    private val snackbarManager: SnackbarManager,
    private val vaultItemRepository: VaultItemRepository,
    private val filterUseCase: FilterUseCase,
    passwordRepository: PasswordRepository,
) : ViewModel() {

    private val allItems = vaultItemRepository.observeLiteVaultItems()

    private val submittedSearchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemSource = submittedSearchQuery.flatMapLatest(::queryToItems)

    private val flaggedForDeletion = MutableStateFlow(setOf<ItemId>())
    private val nonDeletedItems = combine(
        itemSource,
        flaggedForDeletion
    ) { items, flagged ->
        items.filterNot { item -> item.vaultItemId in flagged }
    }.distinctUntilChanged()


    private val passwordScores = passwordRepository.observePasswordScores()

    private val filterState = MutableStateFlow(FilterState.Default)
    private val filteredItems = combine(
        nonDeletedItems,
        filterState,
        passwordScores,
    ) { items, filter, scores ->
        filterUseCase(filter, items, scores)
    }.distinctUntilChanged()

    private val searchResults = MutableStateFlow(listOf<LiteItem>())
    private val selectedItemIds = MutableStateFlow(emptySet<ItemId>())

    val listItemState = combine(
        filteredItems,
        searchResults,
        selectedItemIds,
        submittedSearchQuery,
    ) { items, searchResults, selectedIds, submittedSearchQuery ->
        ListItemState(
            items = items,
            searchResults = searchResults,
            hasSearchQuery = submittedSearchQuery.isNotBlank(),
            selectedItemIds = selectedIds,
        )
    }.onStart {
        observeSearchState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListItemState()
    )

    private val availableFilterOptions = combine(
        nonDeletedItems,
        passwordScores,
    ) { items, scores ->
        items.toAvailableFilterOptions(scores)
    }.distinctUntilChanged()

    val filterBottomSheetState = combine(
        filterState,
        availableFilterOptions,
    ) { filter, available ->
        filter.toBottomSheetState(available, restrictedItemType)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilterBottomSheetState(),
    )

    private val _event = Channel<Event>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    val searchTextFieldState = TextFieldState()

    fun onFilterAction(action: FilterAction) {
        when (action) {
            is FilterAction.SortDirectionChanged -> filterState.update {
                it.copy(sortDirection = action.direction)
            }

            is FilterAction.ItemTypeToggled -> filterState.update {
                it.copy(selectedItemTypes = it.selectedItemTypes.toggle(action.itemType))
            }

            is FilterAction.LabelToggled -> filterState.update {
                it.copy(selectedLabels = it.selectedLabels.toggle(action.label))
            }

            is FilterAction.ScoreToggled -> filterState.update {
                it.copy(selectedScores = it.selectedScores.toggle(action.score))
            }

            is FilterAction.ClearFilters -> filterState.update { FilterState.Default }
        }
    }

    private fun <T> Set<T>.toggle(element: T): Set<T> =
        if (element in this) this - element else this + element

    private suspend fun queryToItems(query: String): Flow<List<LiteItem>> =
        (if (query.isBlank()) allItems
        else flowOf(vaultItemRepository.searchVaultItem(query, restrictedItemType)))

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSearchState() {
        snapshotFlow { searchTextFieldState.text }
            .debounce(300.milliseconds)
            .flatMapLatest {
                queryToItems(it.toString())
            }
            .distinctUntilChanged()
            .onEach { items ->
                searchResults.update { items }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    fun onSubmitQuery() {
        submittedSearchQuery.update { searchTextFieldState.text.toString() }
    }

    fun resetToMatchSubmittedQuery() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd(submittedSearchQuery.value)
    }

    fun onClearQuery() {
        searchTextFieldState.clearText()
        submittedSearchQuery.update { "" }
    }

    fun onDelete(itemId: ItemId) {
        updateItemSelectionState(itemId, selected = false)
        updateItemDeletionState(itemId, deleted = true)
        _event.trySend(
            Event.ItemDeleted(
                itemId,
                listItemState.value.items.firstOrNull()?.vaultItemId
            )
        )

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


    fun onItemClick(itemId: ItemId, forceSkipSelection: Boolean = false) {
        if (enableSelection && !forceSkipSelection && selectedItemIds.value.isNotEmpty()) {
            val isSelected = itemId in selectedItemIds.value
            updateItemSelectionState(itemId, selected = !isSelected)
        } else {
            _event.trySend(Event.ItemSelected(itemId))
        }
    }

    fun onItemLongClick(itemId: ItemId) {
        if (enableSelection)
            updateItemSelectionState(itemId, selected = true)

        _event.trySend(Event.ItemLongClicked(itemId))
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
        // Delete all flagged items, once the viewmodel is being cleared
        val pendingDeletions = flaggedForDeletion.getAndUpdate { emptySet() }
        if (pendingDeletions.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                pendingDeletions.forEach { itemId ->
                    vaultItemRepository.deleteItem(itemId)
                }
            }
        }
    }
}

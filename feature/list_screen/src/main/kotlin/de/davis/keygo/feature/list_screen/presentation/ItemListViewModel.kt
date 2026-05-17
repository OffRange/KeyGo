package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.getIdOrNull
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
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
import de.davis.keygo.feature.vault.domain.usecase.ObserveVaultsAndSelectionUseCase
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
import kotlinx.coroutines.flow.map
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
    private val itemRepository: ItemRepository,
    private val filterUseCase: FilterUseCase,
    observeVaultsAndSelection: ObserveVaultsAndSelectionUseCase,
    loginRepository: LoginRepository,
) : ViewModel() {

    private val vaultsAndSelection = observeVaultsAndSelection()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val vaultSpecificItems = vaultsAndSelection.flatMapLatest { vaultsAndSelection ->
        itemRepository.observeLiteVaultItems(vaultsAndSelection.selection.getIdOrNull())
    }

    private val submittedSearchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemSource = submittedSearchQuery.flatMapLatest(::queryToItems)

    private val flaggedForDeletion = MutableStateFlow(setOf<ItemId>())
    private val nonDeletedItems = combine(
        itemSource,
        flaggedForDeletion
    ) { items, flagged ->
        items.filterNot { item -> item.id in flagged }
    }.distinctUntilChanged()

    private val passwordScores = loginRepository.observePasswordScores()

    private val filterState = MutableStateFlow(FilterState.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tagFilteredItemIds: Flow<Set<ItemId>?> = filterState
        .map { it.selectedLabels }
        .distinctUntilChanged()
        .flatMapLatest { labels ->
            if (labels.isEmpty()) flowOf(null)
            else itemRepository.observeItemIdsForTags(labels)
        }

    private val filteredItems = combine(
        nonDeletedItems,
        filterState,
        passwordScores,
        tagFilteredItemIds,
    ) { items, filter, scores, tagIds ->
        filterUseCase(filter, items, scores, tagIds)
    }.distinctUntilChanged()

    private val searchResults = MutableStateFlow(listOf<LiteItem>())
    private val selectedItemIds = MutableStateFlow(emptySet<ItemId>())
    private val highlightedId = MutableStateFlow<ItemId?>(null)
    private val _isVaultFlowVisible = MutableStateFlow(false)

    val listItemState = combine7(
        vaultsAndSelection,
        filteredItems,
        searchResults,
        selectedItemIds,
        submittedSearchQuery,
        highlightedId,
        _isVaultFlowVisible,
    ) { vaultsAndSel, items, searchResults, selectedIds, submittedSearchQuery, highlightedId, isVaultFlowVisible ->
        ListItemState(
            items = items,
            searchResults = searchResults,
            hasSearchQuery = submittedSearchQuery.isNotBlank(),
            selectedItemIds = selectedIds,
            highlightedId = highlightedId,
            isVaultFlowVisible = isVaultFlowVisible,
            vaults = vaultsAndSel.vaults,
            vaultContext = vaultsAndSel.selection,
        )
    }.distinctUntilChanged()
        .onStart {
            observeSearchState()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListItemState()
        )

    private val availableFilterOptions = combine(
        nonDeletedItems,
        passwordScores,
        itemRepository.observeAllTags(),
    ) { items, scores, allTags ->
        items.toAvailableFilterOptions(scores, allTags)
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
                it.copy(selectedScores = it.selectedScores.toggle(action.passwordScore))
            }

            FilterAction.ShowOnlyPinnedToggled -> filterState.update {
                it.copy(onlyPinned = !it.onlyPinned)
            }

            is FilterAction.ClearFilters -> filterState.update { FilterState.Default }
        }
    }

    fun onVaultSelectorClick() {
        _isVaultFlowVisible.update { true }
    }

    fun onDismissVaultFlow() {
        _isVaultFlowVisible.update { false }
    }

    private fun <T> Set<T>.toggle(element: T): Set<T> =
        if (element in this) this - element else this + element

    private suspend fun queryToItems(
        query: String,
        forceSearchAllVaults: Boolean = false
    ): Flow<List<LiteItem>> =
        (if (!forceSearchAllVaults && query.isBlank()) vaultSpecificItems
        else flowOf(itemRepository.searchVaultItem(query, restrictedItemType)))

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSearchState() {
        snapshotFlow { searchTextFieldState.text }
            .debounce(300.milliseconds)
            .flatMapLatest {
                queryToItems(it.toString(), forceSearchAllVaults = true)
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

    fun resetHighlight() {
        highlightedId.update { null }
    }

    fun onClearQuery() {
        searchTextFieldState.clearText()
        submittedSearchQuery.update { "" }
    }

    fun onDelete(itemId: ItemId) {
        updateItemSelectionState(itemId, selected = false)
        updateItemDeletionState(itemId, deleted = true)

        val firstItemId = listItemState.value.items.firstOrNull()?.id
        if (highlightedId.value == itemId)
            highlightedId.update { firstItemId }

        _event.trySend(Event.ItemDeleted(itemId, firstItemId))

        snackbarManager.sendMessage(
            ItemDeletedMessage(
                onClick = {
                    updateItemDeletionState(itemId, deleted = false)
                },
                onDismiss = {
                    viewModelScope.launch {
                        itemRepository.deleteItem(itemId)

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
            highlightedId.update { itemId }
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
                    itemRepository.deleteItem(itemId)
                }
            }
        }
    }
}

private fun <T1, T2, T3, T4, T5, T6, T7, R> combine7(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    transform: (T1, T2, T3, T4, T5, T6, T7) -> R
): Flow<R> = combine(flow1, flow2, flow3, flow4, flow5, flow6, flow7) { arrayOfFlows ->
    @Suppress("UNCHECKED_CAST")
    transform(
        arrayOfFlows[0] as T1,
        arrayOfFlows[1] as T2,
        arrayOfFlows[2] as T3,
        arrayOfFlows[3] as T4,
        arrayOfFlows[4] as T5,
        arrayOfFlows[5] as T6,
        arrayOfFlows[6] as T7,
    )
}

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
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.combine
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.usecase.ObserveCardExpiryStatusesUseCase
import de.davis.keygo.feature.list_screen.domain.usecase.ObserveFilterResultUseCase
import de.davis.keygo.feature.list_screen.domain.usecase.RankSearchResultsUseCase
import de.davis.keygo.feature.list_screen.presentation.mapper.toAvailableFilterOptions
import de.davis.keygo.feature.list_screen.presentation.mapper.toBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.Event
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.ItemSelection
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
import de.davis.keygo.feature.list_screen.presentation.model.SearchState
import de.davis.keygo.feature.vault.domain.usecase.ObserveVaultsAndSelectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Paces the search query rather than the keystroke: each fire runs a cross-vault `LIKE` scan with a
 * tag join, so halving this doubles those scans.
 */
private val SEARCH_DEBOUNCE = 300.milliseconds

@KoinViewModel
internal class ItemListViewModel(
    @InjectedParam private val enableSelection: Boolean,
    @InjectedParam private val restrictedItemType: VaultItemType?,
    private val itemRepository: ItemRepository,
    private val rankSearchResults: RankSearchResultsUseCase,
    observeAllTags: ObserveAllTagsSortedUseCase,
    observeVaultsAndSelection: ObserveVaultsAndSelectionUseCase,
    observeFilterResult: ObserveFilterResultUseCase,
    observeCardExpiryStatuses: ObserveCardExpiryStatusesUseCase,
) : ViewModel() {

    private val vaultsAndSelection = observeVaultsAndSelection()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val vaultSpecificItems = vaultsAndSelection.flatMapLatest { vaultsAndSelection ->
        itemRepository.observeLiteVaultItems(vaultsAndSelection.selection.getIdOrNull())
    }

    private val submittedSearchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemSource = submittedSearchQuery
        .flatMapLatest(::queryToItems)
        .distinctUntilChanged()

    private val cardExpiryStatuses = observeCardExpiryStatuses().distinctUntilChanged()

    private val filterState = MutableStateFlow(FilterState.Default)
    private val isFilterSheetVisible = MutableStateFlow(false)

    // Everything selected since the sheet opened. Those chips stay on screen until it closes, even
    // once deselected and carried by no item, so the sheet never reshuffles under the user's finger.
    private val retainedSelections = MutableStateFlow(FacetSelections.None)

    // Shared so the list and the filter sheet read off one upstream subscription instead of each
    // recomputing the filter pipeline independently.
    private val filterResult = observeFilterResult(itemSource, filterState)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val selection = MutableStateFlow(ItemSelection())
    private val _isVaultFlowVisible = MutableStateFlow(false)
    private val _isDeleteConfirmationVisible = MutableStateFlow(false)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchState = snapshotFlow { searchTextFieldState.text.toString() }
        .debounce(SEARCH_DEBOUNCE)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            itemRepository.searchVaultItem(query, restrictedItemType)
                .map { SearchState(query, rankSearchResults(query, it)) }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        // Nobody has searched yet, and combine withholds its first emission until every input has
        // emitted: without this the list screen's first render would wait on a full cross-vault
        // search for the empty query.
        .onStart { emit(SearchState()) }

    val listItemState = combine(
        vaultsAndSelection,
        filterResult,
        searchState,
        selection,
        submittedSearchQuery,
        _isVaultFlowVisible,
        _isDeleteConfirmationVisible,
        cardExpiryStatuses,
    ) { vaultsAndSel, filterResult, searchState, selection, submittedSearchQuery, isVaultFlowVisible, isDeleteConfirmationVisible, expiryStatuses ->
        ListItemState(
            items = filterResult.items,
            isEmptyBecauseOfFilter = filterResult.isEmptyBecauseOfFilter,
            cardExpiryStatuses = expiryStatuses,
            searchState = searchState,
            hasSearchQuery = submittedSearchQuery.isNotBlank(),
            selection = selection,
            isVaultFlowVisible = isVaultFlowVisible,
            isDeleteConfirmationVisible = isDeleteConfirmationVisible,
            vaults = vaultsAndSel.vaults,
            vaultContext = vaultsAndSel.selection,
        )
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListItemState(),
        )

    val filterBottomSheetState = combine(
        filterState,
        filterResult.map { it.available }.distinctUntilChanged(),
        observeAllTags(),
        retainedSelections,
        isFilterSheetVisible,
    ) { filter, available, allTags, retained, isVisible ->
        filter.toBottomSheetState(
            available.toAvailableFilterOptions(allTags),
            restrictedItemType,
            retained,
            isVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FilterBottomSheetState(),
    )

    private val _event = Channel<Event>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    val searchTextFieldState = TextFieldState()

    fun onFilterAction(action: FilterAction) {
        val filter = filterState.updateAndGet {
            when (action) {
                is FilterAction.SortDirectionChanged -> it.copy(sortDirection = action.direction)
                is FilterAction.Toggled<*> -> it.copy(selections = action.applyTo(it.selections))
                FilterAction.ClearFilters -> FilterState.Default
            }
        }

        if (isFilterSheetVisible.value) retainedSelections.update { it + filter.selections }
    }

    fun onShowFilterSheet() {
        retainedSelections.update { filterState.value.selections }
        isFilterSheetVisible.update { true }
    }

    fun onDismissFilterSheet() {
        isFilterSheetVisible.update { false }
        retainedSelections.update { FacetSelections.None }
    }

    fun onVaultSelectorClick() {
        _isVaultFlowVisible.update { true }
    }

    fun onDismissVaultFlow() {
        _isVaultFlowVisible.update { false }
    }

    private fun queryToItems(query: String): Flow<List<LiteItem>> =
        if (query.isBlank()) vaultSpecificItems
        else itemRepository.searchVaultItem(query, restrictedItemType)

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

    fun onSelectAll() {
        if (!enableSelection) return

        selection.update { ItemSelection.of(listItemState.value.items) }
    }

    fun onClearSelection() {
        selection.update { ItemSelection() }
    }

    fun onDeleteSelectedRequest() {
        if (selection.value.isActive) _isDeleteConfirmationVisible.update { true }
    }

    fun onPinSelectedRequest() {
        val current = selection.value
        if (!current.isActive) return

        val pinned = !current.allPinned
        selection.update { it.withAllPinned(pinned) }

        viewModelScope.launch { itemRepository.setPinned(current.ids, pinned) }
    }

    fun onDismissDeleteConfirmation() {
        _isDeleteConfirmationVisible.update { false }
    }

    fun onConfirmDeleteSelected() {
        _isDeleteConfirmationVisible.update { false }

        val deleted = selection.getAndUpdate { ItemSelection() }.ids
        if (deleted.isEmpty()) return

        // Read off the list still on screen: after the delete lands the flow has already dropped
        // these rows, so the survivor has to be picked before the write.
        val firstItemId = listItemState.value.items.firstOrNull { it.id !in deleted }?.id

        viewModelScope.launch {
            itemRepository.deleteItems(deleted)
            _event.trySend(Event.ItemsDeleted(deleted, firstItemId))
        }
    }


    fun onItemClick(itemId: ItemId, forceSkipSelection: Boolean = false) {
        if (enableSelection && !forceSkipSelection && selection.value.isActive) {
            val isSelected = itemId in selection.value.ids
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
        // The pinned flag is read off the row being selected: the selection carries it from here
        // on, so the top bar knows whether it can offer an unpin without asking the list again.
        val pinned = listItemState.value.items.any { it.id == id && it.pinned }
        selection.update { currentSelection ->
            if (selected) currentSelection.select(id, pinned)
            else currentSelection.deselect(id)
        }
    }

}

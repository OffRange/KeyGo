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
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.combine
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.usecase.FilterUseCase
import de.davis.keygo.feature.list_screen.presentation.mapper.toAvailableFilterOptions
import de.davis.keygo.feature.list_screen.presentation.mapper.toBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.Event
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val filterUseCase: FilterUseCase,
    observeAllTags: ObserveAllTagsSortedUseCase,
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
    private val itemSource = submittedSearchQuery
        .flatMapLatest(::queryToItems)
        .distinctUntilChanged()

    private val passwordScores = loginRepository.observePasswordScores()

    private val filterState = MutableStateFlow(FilterState.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tagFilteredItemIds: Flow<Set<ItemId>?> = filterState
        .map { it.selectedTags }
        .distinctUntilChanged()
        .flatMapLatest { tags ->
            if (tags.isEmpty()) flowOf(null)
            else itemRepository.observeItemIdsForTags(tags)
        }

    private val filteredItems = combine(
        itemSource,
        filterState,
        passwordScores,
        tagFilteredItemIds,
    ) { items, filter, scores, tagIds ->
        filterUseCase(filter, items, scores, tagIds)
    }.distinctUntilChanged()

    private val selectedItemIds = MutableStateFlow(emptySet<ItemId>())
    private val highlightedId = MutableStateFlow<ItemId?>(null)
    private val _isVaultFlowVisible = MutableStateFlow(false)
    private val _isDeleteConfirmationVisible = MutableStateFlow(false)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchResults = snapshotFlow { searchTextFieldState.text }
        .debounce(SEARCH_DEBOUNCE)
        .flatMapLatest {
            queryToItems(it.toString(), forceSearchAllVaults = true)
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        // Nobody has searched yet, and combine withholds its first emission until every input has
        // emitted: without this the list screen's first render would wait on a full cross-vault
        // search for the empty query.
        .onStart { emit(emptyList()) }

    val listItemState = combine(
        vaultsAndSelection,
        filteredItems,
        searchResults,
        selectedItemIds,
        submittedSearchQuery,
        highlightedId,
        _isVaultFlowVisible,
        _isDeleteConfirmationVisible,
    ) { vaultsAndSel, items, searchResults, selectedIds, submittedSearchQuery, highlightedId, isVaultFlowVisible, isDeleteConfirmationVisible ->
        ListItemState(
            items = items,
            searchResults = searchResults,
            hasSearchQuery = submittedSearchQuery.isNotBlank(),
            selectedItemIds = selectedIds,
            highlightedId = highlightedId,
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

    private val availableFilterOptions = combine(
        itemSource,
        passwordScores,
        itemRepository.observeTagsByItem(),
        observeAllTags(),
    ) { items, scores, tagsByItem, allTags ->
        items.toAvailableFilterOptions(scores, tagsByItem, allTags)
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

            is FilterAction.TagToggled -> filterState.update {
                it.copy(selectedTags = it.selectedTags.toggle(action.tag))
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

    private fun queryToItems(
        query: String,
        forceSearchAllVaults: Boolean = false
    ): Flow<List<LiteItem>> =
        (if (!forceSearchAllVaults && query.isBlank()) vaultSpecificItems
        else itemRepository.searchVaultItem(query, restrictedItemType))

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

    fun onSelectAll() {
        if (!enableSelection) return

        selectedItemIds.update { listItemState.value.items.mapTo(mutableSetOf()) { it.id } }
    }

    fun onClearSelection() {
        selectedItemIds.update { emptySet() }
    }

    fun onDeleteSelectedRequest() {
        if (selectedItemIds.value.isNotEmpty()) _isDeleteConfirmationVisible.update { true }
    }

    fun onPinSelectedRequest() {
        // TODO
    }

    fun onDismissDeleteConfirmation() {
        _isDeleteConfirmationVisible.update { false }
    }

    fun onConfirmDeleteSelected() {
        _isDeleteConfirmationVisible.update { false }

        val deleted = selectedItemIds.getAndUpdate { emptySet() }
        if (deleted.isEmpty()) return

        // Read off the list still on screen: after the delete lands the flow has already dropped
        // these rows, so the survivor has to be picked before the write.
        val firstItemId = listItemState.value.items.firstOrNull { it.id !in deleted }?.id
        if (highlightedId.value in deleted)
            highlightedId.update { firstItemId }

        viewModelScope.launch {
            itemRepository.deleteItems(deleted)
            _event.trySend(Event.ItemsDeleted(deleted, firstItemId))
        }
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

}

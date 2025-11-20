package de.davis.keygo.dashboard.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.ItemIdNone
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItemSearchResult
import de.davis.keygo.core.item.domain.repository.VaultItemRepository
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.presentation.snackbar.ItemDeletedMessage
import de.davis.keygo.dashboard.domain.model.Filter
import de.davis.keygo.dashboard.domain.usecase.FilterUseCase
import de.davis.keygo.dashboard.presentation.model.DashboardEvent
import de.davis.keygo.dashboard.presentation.model.DashboardListUIState
import de.davis.keygo.dashboard.presentation.model.DashboardUIEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
class DashboardViewModel(
    private val snackbarManager: SnackbarManager,
    private val vaultItemRepository: VaultItemRepository,
    filterItems: FilterUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val textFieldState = TextFieldState()

    private val submittedSearchQuery = MutableStateFlow("")

    private val searchResult = MutableStateFlow(emptyList<LiteVaultItemSearchResult>())

    private val filter = MutableStateFlow<Filter>(Filter.Alphanumerical())
    private val flaggedForDeletion = MutableStateFlow(setOf<ItemId>())

    private val nonDeletedSearchResult = combine(
        flaggedForDeletion,
        searchResult
    ) { flaggedForDeletion, searchResult ->
        filterItems(
            filter = Filter.Alphanumerical(),
            namedItems = searchResult.filterNot { it.vaultItemId in flaggedForDeletion }
        )
    }

    private val repoFilteredItems = combine(
        vaultItemRepository.observeLiteVaultItems(),
        filter,
        flaggedForDeletion
    ) { items, filter, flaggedForDeletion ->
        filterItems(
            filter = filter,
            namedItems = items.filterNot { it.vaultItemId in flaggedForDeletion }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainViewItems = submittedSearchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            repoFilteredItems
        } else {
            nonDeletedSearchResult // TODO introduce suggestion and full-search queries
        }
    }

    private val selectedItemIds = MutableStateFlow(setOf<ItemId>())

    private val openedItemId = MutableStateFlow(ItemIdNone)

    val listUiState = combine(
        mainViewItems,
        selectedItemIds,
        openedItemId,
        nonDeletedSearchResult,
    ) { items, selectedItemIds, openedItemId, searchResult ->
        DashboardListUIState(
            textFieldState = textFieldState,
            items = items.toImmutableList(),
            searchResult = searchResult.toImmutableList(),
            selectedItemIds = selectedItemIds.toImmutableSet(),
            openedItemId = if (openedItemId == -2L) items.firstOrNull()?.vaultItemId
                ?: ItemIdNone else openedItemId,
        )
    }.onStart {
        runSearch()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardListUIState(textFieldState)
    )

    private val eventChannel = Channel<DashboardEvent>()
    val eventFlow = eventChannel.receiveAsFlow()


    init {
        val totpUri = savedStateHandle.toRoute<RouteDestination.Home.Root>().totpUri
        totpUri?.let {
            viewModelScope.launch {
                eventChannel.send(DashboardEvent.CreateOrUpdate(it))
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun runSearch() {
        snapshotFlow {
            textFieldState.text
        }.debounce(300.milliseconds)
            .distinctUntilChanged()
            .onEach { query ->
                searchResult.update {
                    performSearch(query.toString())
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    suspend fun performSearch(query: String): List<LiteVaultItemSearchResult> {
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
                textFieldState.setTextAndPlaceCursorAtEnd(submittedSearchQuery.value)
            }

            is DashboardUIEvent.CloseItem -> {
                openedItemId.update { ItemIdNone }
            }

            is DashboardUIEvent.OpenFirstItem -> openItem(-2L)

            is DashboardUIEvent.OnCreateNewItemRequest -> viewModelScope.launch {
                eventChannel.send(DashboardEvent.CreateNewItemRequest(event.itemType))
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
                                    vaultItemRepository.deleteItem(id)

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

    private fun openItem(id: ItemId) {
        openedItemId.update { id }
    }

    private fun clearSearch() {
        textFieldState.clearText()
        submittedSearchQuery.update { "" }
    }

    private fun toggleSelection(id: ItemId) {
        selectedItemIds.update {
            if (id in it) it - id
            else it + id
        }
    }

    private fun updateSelection(id: ItemId, select: Boolean = true) {
        selectedItemIds.update {
            if (select) it + id
            else it - id
        }
    }

    private fun updateDeletionFlag(id: ItemId, flag: Boolean = true) {
        flaggedForDeletion.update {
            if (flag) it + id
            else it - id
        }
    }
}
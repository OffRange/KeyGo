package de.davis.keygo.feature.item.create.presentation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.feature.item.create.presentation.model.ItemUiEvent
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState
import de.davis.keygo.feature.item.create.presentation.model.SharedItemState
import de.davis.keygo.feature.item.create.presentation.model.VaultsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Base ViewModel for the item create/edit screens. Owns the machinery every item type shares: the
 * selected vault, the assigned-tag set, the observable vault list and tag suggestions, and the
 * navigation channel raised once an item is persisted. It also assembles the [ItemUiState.Loading]
 * -> [ItemUiState.Ready] [state] flow so subclasses only express their own item-specific state and
 * the pure mapping into their UI state.
 *
 * @param S the subclass' item-specific state (e.g. the form fields)
 */
internal abstract class ItemViewModel<S>(
    private val vaultContextRepository: VaultContextRepository,
    private val itemRepository: ItemRepository,
    observeAllTags: ObserveAllTagsSortedUseCase,
    vaultRepository: VaultRepository,
) : ViewModel() {

    protected val nameTextFieldState = TextFieldState()
    protected val notesTextFieldState = TextFieldState()

    /** The id of the item being edited, or `null` when creating a new one. */
    protected var itemId: ItemId? = null

    protected val selectedVaultId = MutableStateFlow<VaultId?>(null)
    private val assignedTags = MutableStateFlow<Set<Tag>>(emptySet())
    private val nameExists = MutableStateFlow(false)

    private val vaults = combine(
        vaultRepository.observeAllVaultMetadata(),
        selectedVaultId.filterNotNull(),
    ) { metadata, selected ->
        VaultsState(vaults = metadata, selectedVaultId = selected)
    }

    private val shared: Flow<SharedItemState> = combine(
        vaults,
        observeAllTags(),
        assignedTags,
        nameExists,
    ) { vaults, allTags, assigned, nameExists ->
        SharedItemState(
            nameTextFieldState = nameTextFieldState,
            notesTextFieldState = notesTextFieldState,
            nameExists = nameExists,
            vaultsState = vaults,
            itemAssignedTags = assigned,
            tagsForSuggestion = (allTags - assigned).toSet(),
        )
    }.distinctUntilChanged()

    /** The subclass' item-specific state, paired with [shared] into [ItemUiState.Ready]. */
    protected abstract val itemState: Flow<S>

    /**
     * Lazy so the `combine` body — which reads the abstract [itemState] — runs on first collection
     * rather than during construction, by which point the subclass is fully initialized (base
     * property initializers otherwise run before the subclass').
     */
    val state: StateFlow<ItemUiState<S>> by lazy {
        combine(itemState, shared) { item, shared -> ItemUiState.Ready(item, shared) }
            .onStart {
                primeActiveVaultId()
                observeNameTextField()
                onSubscribed()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ItemUiState.Loading,
            )
    }

    private val itemCreatedEventChannel = Channel<ItemId?>()
    val itemCreatedEvent = itemCreatedEventChannel.receiveAsFlow()

    /** Hook for subclasses to start their own observers when [state] gains its first subscriber. */
    protected open suspend fun onSubscribed() {}

    @OptIn(FlowPreview::class)
    private fun observeNameTextField() {
        snapshotFlow { nameTextFieldState.text }
            .debounce(150.milliseconds)
            .combine(selectedVaultId.filterNotNull()) { input, vaultId ->
                itemRepository.doesNameExist(
                    input.toString(),
                    excludeId = itemId,
                    vaultId = vaultId,
                )
            }
            .distinctUntilChanged()
            .onEach { exists -> nameExists.value = exists }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    protected fun setSelectedVaultId(vaultId: VaultId) {
        selectedVaultId.value = vaultId
    }

    protected fun setAssignedTags(tags: Set<Tag>) {
        assignedTags.value = tags
    }

    protected fun navigateUp(itemId: ItemId? = null) {
        viewModelScope.launch {
            itemCreatedEventChannel.send(itemId)
        }
    }

    private fun primeActiveVaultId() {
        viewModelScope.launch {
            val activeId = vaultContextRepository.getLastInteractedVaultId() ?: return@launch
            selectedVaultId.compareAndSet(null, activeId)
        }
    }

    /** Persists the item. Raise [navigateUp] with the new id on success. */
    protected abstract fun onSubmit()

    protected open fun onBackClick() {
        navigateUp()
    }

    /** Handles the [ItemUiEvent]s common to every item type. */
    protected fun onItemUiEvent(event: ItemUiEvent) {
        when (event) {
            is ItemUiEvent.OnSubmit -> onSubmit()
            is ItemUiEvent.OnBackClick -> onBackClick()
            is ItemUiEvent.OnVaultSelected -> setSelectedVaultId(event.vaultId)
            is ItemUiEvent.OnAddTags -> assignedTags.update { it + event.tags }
            is ItemUiEvent.OnRemoveTag -> assignedTags.update { tags ->
                tags.filterNot { it == event.tag }.toSet()
            }
        }
    }
}

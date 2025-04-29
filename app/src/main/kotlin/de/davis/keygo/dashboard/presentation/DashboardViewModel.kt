package de.davis.keygo.dashboard.presentation

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.usecase.InsertVaultItem
import de.davis.keygo.dashboard.presentation.model.DashboardUIEvent
import de.davis.keygo.dashboard.presentation.model.DashboardUIState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class DashboardViewModel(
    passwordRepository: PasswordRepository,
    insertVaultItem: InsertVaultItem
) : ViewModel() {

    private val textFieldState = TextFieldState()

    private val items = passwordRepository.observeVaultPasswords()

    private val searchResult = MutableStateFlow(emptyList<VaultItem>())

    private val selectedItemIds = MutableStateFlow(setOf<Long>())
    private val selectionMode = selectedItemIds
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val openedItemId = MutableStateFlow(-1L)

    init {
        /*viewModelScope.launch {
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
        }*/
    }

    val uiState = combine(
        items,
        selectedItemIds,
        openedItemId,
        searchResult
    ) { items, selectedItemIds, openedItemId, _ ->
        DashboardUIState(
            textFieldState = textFieldState,
            items = items.sortedBy { it.name }.toImmutableList(),
            selectedItemIds = selectedItemIds.toImmutableSet(),
            openedItemId = openedItemId
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
                selectedItemIds.update {
                    val id = event.vaultId
                    if (id in it) it - id
                    else it + id
                }
            } else {
                openedItemId.update {
                    event.vaultId
                }
            }
        }

        is DashboardUIEvent.OnLongClicked -> {
            selectedItemIds.update {
                it + event.vaultId
            }
        }
    }
}
package de.davis.keygo.feature.item.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ViewVaultItemViewModel(
    private val itemRepository: ItemRepository,
) : ViewModel() {

    private val _itemId = MutableStateFlow<ItemId?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val itemType: StateFlow<VaultItemType?> = _itemId
        .filterNotNull()
        .distinctUntilChanged()
        .mapLatest { itemRepository.getItemType(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun init(itemId: ItemId) {
        _itemId.update { itemId }
    }
}

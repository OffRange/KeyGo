package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

@OptIn(ExperimentalMaterial3Api::class)
fun EntryProviderScope<NavKey>.itemListEntries(
    onItemClick: (ItemId) -> Unit,
    onCreateRequest: (VaultItemType) -> Unit,
    onItemLongClick: (ItemId) -> Unit = {},
    restrictedItemType: VaultItemType? = null,
    notFoundStrategy: NoItemStrategy = NoItemStrategy.ShowCreateNewItemCard,
    enableDeletion: Boolean = false,
    enableSelection: Boolean = false,
    dockedSearchResults: Boolean = false,
) {
    entry<ItemListRoute> {
        ItemListScreen(
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onCreateItemRequest = onCreateRequest,
            restrictedItemType = restrictedItemType,
            notFoundStrategy = notFoundStrategy,
            enableDeletion = enableDeletion,
            enableSelection = enableSelection,
            dockedSearchResults = dockedSearchResults,
        )
    }
}

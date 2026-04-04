package de.davis.keygo.feature.list_screen.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.itemListGraph(
    onItemClick: (ItemId) -> Unit,
    onCreateRequest: (VaultItemType) -> Unit,
    onItemLongClick: (ItemId) -> Unit = {},
    itemTypeWhitelist: ItemTypeWhitelist = ItemTypeWhitelist.ALL,
    notFoundStrategy: NoItemStrategy = NoItemStrategy.ShowCreateNewItemCard,
    enableDeletion: Boolean = false,
    enableSelection: Boolean = false,
    dockedSearchResults: Boolean = false,
) {
    composable<ItemListRoute> {
        ItemListScreen(
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onCreateItemRequest = onCreateRequest,
            itemTypeWhitelist = itemTypeWhitelist,
            notFoundStrategy = notFoundStrategy,
            enableDeletion = enableDeletion,
            enableSelection = enableSelection,
            dockedSearchResults = dockedSearchResults,
        )
    }
}
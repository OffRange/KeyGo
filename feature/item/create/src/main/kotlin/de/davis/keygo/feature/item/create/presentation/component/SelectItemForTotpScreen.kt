package de.davis.keygo.feature.item.create.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import de.davis.keygo.feature.item.core.R as ItemCoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectItemForTotpScreen(
    suggestedItemIds: Set<ItemId>,
    onItemClick: (ItemId) -> Unit,
    onCreateNew: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.select_item_for_totp)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(ItemCoreR.string.back_content_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNew) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.create_new),
                )
            }
        },
    ) { innerPadding ->
        ItemListScreen(
            onItemClick = onItemClick,
            onItemLongClick = { },
            onCreateItemRequest = { onCreateNew() },
            restrictedItemType = VaultItemType.Login,
            suggestedItemIds = suggestedItemIds,
            notFoundStrategy = NoItemStrategy.ShowMessage,
            enableDeletion = false,
            enableSelection = false,
            dockedSearchResults = false,
            modifier = Modifier
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding),
        )
    }
}

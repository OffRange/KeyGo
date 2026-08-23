package de.davis.keygo.feature.item.create.presentation.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy

/**
 * Asks which item a scanned code belongs to, listing every login with the ones on the code's own
 * domain grouped first.
 *
 * The screen carries no back affordance. It is the first step of a deep-linked import, which
 * replaced the whole back stack on its way here, so there is nothing behind it: back leaves the app
 * rather than revealing a dashboard the user never opened.
 *
 * @param loading shows the chrome without the list, for the moment before the vault is readable.
 * Choosing anything then would have nothing to choose from, so both actions are withheld too.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectItemForTotpScreen(
    suggestedItemIds: Set<ItemId>,
    onItemClick: (ItemId) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val activity = LocalActivity.current
    BackHandler { activity?.finish() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.select_item_for_totp)) })
        },
        floatingActionButton = {
            if (!loading)
                FloatingActionButton(onClick = onCreateNew) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_new),
                    )
                }
        },
    ) { innerPadding ->
        val content = Modifier
            .consumeWindowInsets(innerPadding)
            .padding(innerPadding)

        when (loading) {
            true -> Box(
                modifier = content.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ContainedLoadingIndicator()
            }

            false -> ItemListScreen(
                onItemClick = onItemClick,
                onItemLongClick = { },
                onCreateItemRequest = { onCreateNew() },
                restrictedItemType = VaultItemType.Login,
                suggestedItemIds = suggestedItemIds,
                notFoundStrategy = NoItemStrategy.ShowMessage,
                enableDeletion = false,
                enableSelection = false,
                dockedSearchResults = false,
                modifier = content,
            )
        }
    }
}

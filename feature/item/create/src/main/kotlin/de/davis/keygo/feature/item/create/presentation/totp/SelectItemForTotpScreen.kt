package de.davis.keygo.feature.item.create.presentation.totp

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Asks which item a scanned code belongs to, listing every login with the ones on the code's own
 * domain grouped first. Only logins are listed, because a TOTP secret can only be attached to one.
 *
 * The screen carries no back affordance and no back handler. It is the first step of a deep-linked
 * import, which replaced the whole back stack on its way here, so the NavHost does not consume a
 * back press and the activity finishes on its own.
 *
 * It also carries no error surface. A code that cannot be read never reaches this screen, because
 * the redirect that starts the import ends the flow there.
 *
 * @param totpUri the scanned deep link, carried whole so the screen and its ViewModel can parse it
 * independently.
 * @param onItemSelected the user picked an existing login to attach the code to.
 * @param onCreateNew the user chose to attach the code to a login that does not exist yet.
 */
@Composable
fun SelectItemForTotpScreen(
    totpUri: String,
    onItemSelected: (ItemId) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SelectItemForTotpViewModel = koinViewModel { parametersOf(totpUri) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    SelectItemForTotpContent(
        state = state,
        onItemSelected = onItemSelected,
        onCreateNew = onCreateNew,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectItemForTotpContent(
    state: SelectItemForTotpUiState,
    onItemSelected: (ItemId) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.select_item_for_totp)) })
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
            onItemClick = onItemSelected,
            onItemLongClick = { },
            onCreateItemRequest = { onCreateNew() },
            restrictedItemType = VaultItemType.Login,
            suggestedItemIds = state.suggestedItemIds,
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

package de.davis.keygo.feature.totp.presentation

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
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import de.davis.keygo.feature.totp.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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

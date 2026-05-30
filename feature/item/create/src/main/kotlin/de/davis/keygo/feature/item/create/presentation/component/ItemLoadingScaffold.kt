package de.davis.keygo.feature.item.create.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.component.CreateOrModifyItemTopAppBar
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState

@Composable
internal fun <S> ItemContentWrapper(
    itemType: VaultItemType,
    state: ItemUiState<S>,
    onBackClick: () -> Unit,
    content: @Composable (ItemUiState.Ready<S>) -> Unit
) {
    when (state) {
        ItemUiState.Loading -> ItemLoadingScaffold(
            itemType = itemType,
            onBackClick = onBackClick,
        )

        is ItemUiState.Ready -> content(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemLoadingScaffold(itemType: VaultItemType, onBackClick: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CreateOrModifyItemTopAppBar(
                itemType = itemType,
                updating = false,
                onBackClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            ContainedLoadingIndicator()
        }
    }
}

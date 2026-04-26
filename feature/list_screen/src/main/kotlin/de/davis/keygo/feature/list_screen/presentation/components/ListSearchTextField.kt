package de.davis.keygo.feature.list_screen.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.list_screen.R
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.ListItemState
import de.davis.keygo.feature.list_screen.presentation.selectedVaultIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ListSearchTextField(
    searchTextFieldState: TextFieldState,
    searchBarState: SearchBarState,
    uiState: ListItemState,
    onSubmitQuery: () -> Unit,
    onClearQuery: () -> Unit,
    onShowFilterClick: () -> Unit,
    onVaultSelectorClick: () -> Unit,
    filterBottomSheetState: FilterBottomSheetState,
) {
    val scope = rememberCoroutineScope()
    SearchBarDefaults.InputField(
        textFieldState = searchTextFieldState,
        searchBarState = searchBarState,
        onSearch = {
            onSubmitQuery()
            scope.launch { searchBarState.animateToCollapsed() }
        },
        enabled = false, // TODO: remove when b/464761441 is fixed
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            AnimatedContent(
                targetState = !uiState.hasSearchQuery && searchBarState.targetValue == SearchBarValue.Collapsed
            ) {
                when (it) {
                    true -> Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )

                    false -> IconButton(
                        onClick = {
                            onClearQuery()
                            scope.launch { searchBarState.animateToCollapsed() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            }
        },
        trailingIcon = {
            Row {
                IconButton(onClick = onShowFilterClick) {
                    BadgedBox(
                        badge = {
                            if (!filterBottomSheetState.isDefault) Badge()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.filter),
                        )
                    }
                }

                IconButton(
                    onClick = onVaultSelectorClick
                ) {
                    Icon(
                        painter = uiState.vaultState.selectedVaultIcon(),
                        contentDescription = stringResource(R.string.filter),
                    )
                }
            }
        },
        placeholder = {
            Text(text = stringResource(R.string.search_your_vault))
        }
    )
}

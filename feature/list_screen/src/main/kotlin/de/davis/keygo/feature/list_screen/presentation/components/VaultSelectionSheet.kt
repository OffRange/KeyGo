package de.davis.keygo.feature.list_screen.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.list_screen.R
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import de.davis.keygo.feature.list_screen.presentation.AllVaultsIcon
import de.davis.keygo.feature.list_screen.presentation.model.CreateVaultRequest
import de.davis.keygo.feature.list_screen.presentation.model.VaultState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultSelectionSheet(
    vaultState: VaultState,
    onDismiss: () -> Unit,
    onVaultSelected: (SelectedVault) -> Unit,
    onCreateVaultRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            VaultSelectionSheetContent(
                vaultState = vaultState,
                onVaultSelected = onVaultSelected,
                onCreateVaultRequest = onCreateVaultRequest,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultSelectionSheetContent(
    vaultState: VaultState,
    onVaultSelected: (SelectedVault) -> Unit,
    onCreateVaultRequest: () -> Unit,
) {
    val sumAllVaults = vaultState.sumCount
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "vault_selection_all") {
            SegmentedListItem(
                selected = vaultState.selectedVault is SelectedVault.All,
                onClick = { onVaultSelected(SelectedVault.All) },
                shapes = ListItemDefaults.segmentedShapes(0, 2),
                colors = ListItemDefaults.segmentedColors(),
                leadingContent = {
                    Icon(
                        painter = AllVaultsIcon,
                        modifier = Modifier.size(24.dp),
                        contentDescription = null
                    )
                },
                supportingContent = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.vault_item_entry_count,
                            sumAllVaults,
                            sumAllVaults
                        )
                    )
                }
            ) {
                Text(text = stringResource(R.string.all_vaults))
            }
        }

        item(key = "vault_selection_add_new") {
            SegmentedListItem(
                onClick = onCreateVaultRequest,
                shapes = ListItemDefaults.segmentedShapes(1, 2),
                colors = ListItemDefaults.segmentedColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            ) {
                Text(text = stringResource(R.string.create_new_vault))
            }
        }

        val vaultCount = vaultState.vaults.size
        itemsIndexed(
            vaultState.vaults,
            key = { _, metadata -> metadata.vaultId }) { index, metadata ->
            SegmentedListItem(
                selected = (vaultState.selectedVault as? SelectedVault.Id)?.vaultId == metadata.vaultId,
                onClick = { onVaultSelected(SelectedVault.Id(metadata.vaultId)) },
                shapes = if (vaultCount == 1) ListItemDefaults.shapes(MaterialTheme.shapes.large)
                else ListItemDefaults.segmentedShapes(
                    index = index,
                    count = vaultCount,
                ),
                colors = ListItemDefaults.segmentedColors(),
                leadingContent = {
                    Icon(
                        imageVector = metadata.icon.toImageVector(),
                        contentDescription = null
                    )
                },
                supportingContent = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.vault_item_entry_count,
                            metadata.count,
                            metadata.count
                        )
                    )
                }
            ) {
                Text(text = metadata.name)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultCreationSheetContent(
    onBack: () -> Unit,
    onCreate: (CreateVaultRequest) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var selectedIcon by remember { mutableStateOf(Vault.Icon.Default) }
    val textFieldState = rememberTextFieldState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler(onBack = onBack)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = null
                )
            }

            Text(
                text = stringResource(R.string.create_new_vault),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    onCreate(
                        CreateVaultRequest(
                            name = textFieldState.text.toString(),
                            icon = selectedIcon,
                        )
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
            }
        }

        OutlinedTextField(
            state = textFieldState,
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
            label = { Text(text = stringResource(R.string.vault_name)) },
            placeholder = { Text(text = stringResource(R.string.vault_name_placeholder)) },
            leadingIcon = {
                AnimatedContent(selectedIcon) { icon ->
                    Icon(
                        imageVector = icon.toImageVector(),
                        contentDescription = null
                    )
                }
            }
        )

        Text(
            text = stringResource(R.string.icon),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Vault.Icon.entries.forEach { icon ->
                FilledTonalIconToggleButton(
                    checked = selectedIcon == icon,
                    onCheckedChange = { selectedIcon = icon },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(IconButtonDefaults.mediumContainerSize()),
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(
                        imageVector = icon.toImageVector(),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun VaultSelectionSheetContentPreview() {
    val selectedVaultId = remember { newVaultId() }
    KeyGoTheme {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            VaultSelectionSheetContent(
                vaultState = VaultState(
                    vaults = listOf(
                        VaultMetadata(
                            vaultId = newVaultId(),
                            name = "Personal",
                            icon = Vault.Icon.Default,
                            count = 42
                        ),
                        VaultMetadata(
                            vaultId = selectedVaultId,
                            name = "Work",
                            icon = Vault.Icon.Work,
                            count = 1,
                        ),
                    ),
                    selectedVault = SelectedVault.Id(selectedVaultId)
                ),
                onVaultSelected = {},
                onCreateVaultRequest = {},
            )
        }
    }
}

@Preview
@Composable
private fun VaultCreationSheetContentPreview() {
    KeyGoTheme {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            VaultCreationSheetContent(
                onBack = {},
                onCreate = {},
            )
        }
    }
}

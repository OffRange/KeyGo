package de.davis.keygo.feature.vault.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.model.getIdOrNull
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.vault.R
import de.davis.keygo.feature.vault.presentation.AllVaultsIcon
import de.davis.keygo.feature.vault.presentation.model.VaultState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSelectionSheet(
    vaultState: VaultState.Select,
    onDismiss: () -> Unit,
    onVaultContextSelect: (VaultContext) -> Unit,
    onCreateVaultRequest: () -> Unit,
    onEditRequest: (VaultMetadata) -> Unit,
    onDeleteRequest: (VaultMetadata) -> Unit,
    onMoveTo: (VaultId) -> Unit,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
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
                onVaultContextSelect = onVaultContextSelect,
                onCreateVaultRequest = onCreateVaultRequest,
                onEditRequest = onEditRequest,
                onDeleteRequest = onDeleteRequest,
                onMoveTo = onMoveTo,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VaultSelectionSheetContent(
    vaultState: VaultState.Select,
    onVaultContextSelect: (VaultContext) -> Unit,
    onCreateVaultRequest: () -> Unit,
    onEditRequest: (VaultMetadata) -> Unit,
    onDeleteRequest: (VaultMetadata) -> Unit,
    onMoveTo: (VaultId) -> Unit,
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
                selected = vaultState.vaultContext is VaultContext.NoSpecific,
                onClick = { onVaultContextSelect(VaultContext.NoSpecific) },
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
            items = vaultState.vaults,
            key = { _, metadata -> metadata.vaultId }
        ) { index, metadata ->
            SegmentedListItem(
                selected = vaultState.vaultContext.getIdOrNull() == metadata.vaultId,
                onClick = { onVaultContextSelect(VaultContext.ById(metadata.vaultId)) },
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
                },
                trailingContent = {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        TooltipBox(
                            positionProvider =
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                            tooltip = { PlainTooltip { Text(text = stringResource(R.string.edit)) } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.edit)
                                )
                            }
                        }

                        DropdownMenuPopup(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(
                                    0,
                                    if (vaultState.hasMultipleVaults) 2 else 1
                                )
                            ) {
                                val hasItems by remember(metadata.count) {
                                    derivedStateOf { metadata.count > 0 }
                                }

                                DropdownMenuItem(
                                    text = {
                                        Text(text = stringResource(R.string.edit))
                                    },
                                    onClick = {
                                        expanded = false
                                        onEditRequest(metadata)
                                    },
                                    shape = MenuDefaults.itemShape(
                                        0,
                                        if (vaultState.hasMultipleVaults && hasItems) 2 else 1
                                    ).shape,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.edit)
                                        )
                                    }
                                )

                                if (vaultState.hasMultipleVaults && hasItems) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(R.string.move_to))
                                        },
                                        onClick = {
                                            expanded = false
                                            onMoveTo(metadata.vaultId)
                                        },
                                        shape = MenuDefaults.itemShape(1, 2).shape,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Default.DriveFileMove,
                                                contentDescription = stringResource(R.string.move_to)
                                            )
                                        }
                                    )
                                }
                            }

                            if (vaultState.hasMultipleVaults) {
                                Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(1, 2),
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(R.string.delete))
                                        },
                                        onClick = {
                                            expanded = false
                                            onDeleteRequest(metadata)
                                        },
                                        shape = MenuDefaults.trailingItemShape,
                                        colors = MenuDefaults.selectableItemColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                        ),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = stringResource(R.string.delete)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                Text(text = metadata.name)
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
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            VaultSelectionSheetContent(
                vaultState = VaultState.Select(
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
                    vaultContext = VaultContext.ById(selectedVaultId)
                ),
                onVaultContextSelect = {},
                onCreateVaultRequest = {},
                onEditRequest = {},
                onDeleteRequest = {},
                onMoveTo = {},
            )
        }
    }
}

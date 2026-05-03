package de.davis.keygo.feature.vault.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.vault.R
import de.davis.keygo.feature.vault.domain.model.MoveItemsProgress
import de.davis.keygo.feature.vault.presentation.model.VaultState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveVaultDialog(
    vaultState: VaultState.Move,
    onDismissRequest: () -> Unit,
    onDstSelected: (VaultId) -> Unit,
    onConfirm: () -> Unit,
) {
    val isMoving = vaultState.isMoving
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = !isMoving,
            dismissOnClickOutside = !isMoving,
        ),
        title = {
            Text(
                text = stringResource(
                    if (isMoving) R.string.moving_items else R.string.move_to,
                ),
            )
        },
        confirmButton = {
            if (!isMoving)
                Button(
                    onClick = onConfirm,
                    enabled = vaultState.selectedDstVault != null,
                ) {
                    Text(text = stringResource(R.string.move))
                }
        },
        modifier = Modifier.fillMaxWidth(),
        dismissButton = {
            if (!isMoving)
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
        },
        text = {
            if (vaultState.progress != null)
                MoveVaultProgressContent(progress = vaultState.progress)
            else
                MoveVaultDialogContent(
                    vaultState = vaultState,
                    onDstSelected = onDstSelected,
                )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoveVaultProgressContent(progress: MoveItemsProgress) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "moveProgress",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LinearWavyProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.move_progress_count,
                progress.movedCount,
                progress.total,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoveVaultDialogContent(
    vaultState: VaultState.Move,
    onDstSelected: (VaultId) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SrcVaultRow(srcVault = vaultState.srcVault)

        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        DstVaultDropdown(
            dstVaults = vaultState.dstVaults,
            selectedDstVault = vaultState.selectedDstVault,
            onDstSelected = onDstSelected,
        )
    }
}

@Composable
private fun SrcVaultRow(srcVault: VaultMetadata) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = srcVault.icon.toImageVector(),
                contentDescription = null,
            )
        },
        overlineContent = { Text(text = stringResource(R.string.source_vault)) },
        headlineContent = { Text(text = srcVault.name) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DstVaultDropdown(
    dstVaults: List<VaultMetadata>,
    selectedDstVault: VaultMetadata?,
    onDstSelected: (VaultId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedDstVault?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(text = stringResource(R.string.destination_vault)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = selectedDstVault?.let { vault ->
                {
                    Icon(
                        imageVector = vault.icon.toImageVector(),
                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        contentDescription = null,
                    )
                }
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            val optionCount = dstVaults.size
            dstVaults.forEachIndexed { index, vault ->
                DropdownMenuItem(
                    shapes = MenuDefaults.itemShape(index, optionCount),
                    colors = MenuDefaults.selectableItemColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    selected = vault.vaultId == selectedDstVault?.vaultId,
                    text = {
                        Text(
                            text = vault.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        onDstSelected(vault.vaultId)
                        expanded = false
                    },
                    selectedLeadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = vault.icon.toImageVector(),
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null,
                        )
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MoveVaultDialogContentPreview() {
    val srcId = remember { newVaultId() }
    val dst = remember {
        listOf(
            VaultMetadata(
                vaultId = newVaultId(),
                name = "Personal",
                icon = Vault.Icon.Person,
                count = 12,
            ),
            VaultMetadata(
                vaultId = newVaultId(),
                name = "Work",
                icon = Vault.Icon.Work,
                count = 4,
            ),
        )
    }
    KeyGoTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                MoveVaultDialogContent(
                    vaultState = VaultState.Move(
                        srcVault = VaultMetadata(
                            vaultId = srcId,
                            name = "Shopping",
                            icon = Vault.Icon.ShoppingCart,
                            count = 7,
                        ),
                        dstVaults = dst,
                        selectedDstVaultId = dst.first().vaultId,
                    ),
                    onDstSelected = {},
                )
            }
        }
    }
}

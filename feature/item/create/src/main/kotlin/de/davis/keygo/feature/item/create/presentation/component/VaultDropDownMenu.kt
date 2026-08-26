package de.davis.keygo.feature.item.create.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.model.VaultsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VaultDropDownMenu(
    vaultsState: VaultsState,
    onVaultSelect: (VaultId) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedVault = remember(vaultsState) {
        vaultsState.vaults.first { it.vaultId == vaultsState.selectedVaultId }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedVault.name,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = {
                Icon(
                    imageVector = selectedVault.icon.toImageVector(),
                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                    contentDescription = null,
                )
            },
            label = { Text(text = stringResource(R.string.vault)) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            val optionCount = vaultsState.vaults.size
            vaultsState.vaults.forEachIndexed { index, metadata ->
                DropdownMenuItem(
                    shapes = MenuDefaults.itemShape(index, optionCount),
                    colors = MenuDefaults.selectableItemColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    text = {
                        Text(
                            metadata.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    selected = metadata.vaultId == vaultsState.selectedVaultId,
                    onClick = {
                        onVaultSelect(metadata.vaultId)
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
                            imageVector = metadata.icon.toImageVector(),
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
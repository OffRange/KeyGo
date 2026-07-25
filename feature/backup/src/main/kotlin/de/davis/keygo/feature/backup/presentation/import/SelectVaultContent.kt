@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package de.davis.keygo.feature.backup.presentation.import

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.presentation.component.IconBadge

/**
 * Where the import lands. A full step rather than a dropdown: the choice is worth the room, and a
 * dropdown has nowhere to put the name field the "new vault" option needs.
 */
@Composable
internal fun SelectVaultContent(
    vaults: List<VaultMetadata>,
    selectedVaultId: VaultId?,
    creatingNewVault: Boolean,
    newVaultNameState: TextFieldState,
    onSelectVault: (VaultId) -> Unit,
    onCreateNewVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One segment per vault plus the "new vault" row, so the group's rounded ends land correctly.
    val segmentCount = vaults.size + 1

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        itemsIndexed(vaults, key = { _, vault -> vault.vaultId }) { index, vault ->
            VaultSegment(
                vault = vault,
                selected = !creatingNewVault && vault.vaultId == selectedVaultId,
                shapes = ListItemDefaults.segmentedShapes(index, segmentCount),
                onClick = { onSelectVault(vault.vaultId) },
            )
        }

        item(key = "new-vault") {
            NewVaultSegment(
                selected = creatingNewVault,
                nameState = newVaultNameState,
                shapes = ListItemDefaults.segmentedShapes(vaults.size, segmentCount),
                onClick = onCreateNewVault,
            )
        }
    }
}

/**
 * Container behind every segment. Must not be left to [ListItemDefaults.segmentedColors]'s default,
 * which resolves to `colorScheme.surface`, identical to the wizard Scaffold's background in both
 * KeyGo themes, so the rows would have no visible edge at all. Same pin as `MapColumnsContent`.
 */
private val segmentContainerColor
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun VaultSegment(
    vault: VaultMetadata,
    selected: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        leadingContent = {
            IconBadge(
                icon = vault.icon.toImageVector(),
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Text(
                text = pluralStringResource(
                    R.plurals.select_vault_item_count,
                    vault.count,
                    vault.count,
                ),
            )
        },
        trailingContent = {
            if (selected) Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        verticalAlignment = Alignment.CenterVertically,
        // The row is a one-of-many choice, not a button; role announces that, selected announces
        // whether this particular row is the one currently chosen.
        modifier = Modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    ) {
        Text(text = vault.name)
    }
}

@Composable
private fun NewVaultSegment(
    selected: Boolean,
    nameState: TextFieldState,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
            leadingContent = {
                IconBadge(
                    icon = Icons.Default.Add,
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                if (selected) Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        ) {
            Text(text = stringResource(R.string.select_vault_new))
        }

        if (selected) OutlinedTextField(
            state = nameState,
            label = { Text(text = stringResource(R.string.select_vault_name_label)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

private val previewVaults = listOf(
    VaultMetadata(vaultId = newVaultId(), name = "Personal", icon = Vault.Icon.Person, count = 12),
    VaultMetadata(vaultId = newVaultId(), name = "Work", icon = Vault.Icon.Work, count = 4),
    VaultMetadata(vaultId = newVaultId(), name = "Shopping", icon = Vault.Icon.ShoppingCart, count = 7),
)

@Preview(showBackground = true)
@Composable
private fun SelectVaultContentExistingPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SelectVaultContent(
                vaults = previewVaults,
                selectedVaultId = previewVaults.first().vaultId,
                creatingNewVault = false,
                newVaultNameState = TextFieldState(),
                onSelectVault = {},
                onCreateNewVault = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SelectVaultContentNewVaultPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SelectVaultContent(
                vaults = previewVaults,
                selectedVaultId = null,
                creatingNewVault = true,
                newVaultNameState = TextFieldState("passwords"),
                onSelectVault = {},
                onCreateNewVault = {},
            )
        }
    }
}

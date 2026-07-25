package de.davis.keygo.core.item.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.Vault

/**
 * Every [Vault.Icon] as a toggle grid, one of which is [selected].
 *
 * Lives here rather than in a feature module because more than one flow creates vaults, and the
 * picker has to look and behave the same in all of them. Callers supply their own heading.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VaultIconPicker(
    selected: Vault.Icon,
    onSelect: (Vault.Icon) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Vault.Icon.entries.forEach { icon ->
            FilledTonalIconToggleButton(
                checked = selected == icon,
                onCheckedChange = { onSelect(icon) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(IconButtonDefaults.mediumContainerSize()),
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    imageVector = icon.toImageVector(),
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview
@Composable
private fun VaultIconPickerPreview() {
    MaterialTheme {
        Surface {
            VaultIconPicker(selected = Vault.Icon.Work, onSelect = {})
        }
    }
}

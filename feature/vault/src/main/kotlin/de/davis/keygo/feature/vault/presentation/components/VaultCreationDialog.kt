package de.davis.keygo.feature.vault.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.vault.R
import de.davis.keygo.feature.vault.domain.model.VaultCreationError
import de.davis.keygo.feature.vault.presentation.model.VaultState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultCreationDialog(
    vaultState: VaultState.CreateOrUpdate,
    onDismissRequest: () -> Unit,
    onCreateOrEdit: () -> Unit,
    onIconClick: (Vault.Icon) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = onCreateOrEdit
            ) {
                Text(text = stringResource(R.string.create))
            }
        },
        title = {
            Text(text = stringResource(R.string.create_new_vault))
        },
        text = {
            VaultCreationDialogContent(
                vaultState = vaultState,
                onIconClick = onIconClick
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultCreationDialogContent(
    vaultState: VaultState.CreateOrUpdate,
    onIconClick: (Vault.Icon) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        OutlinedTextField(
            state = vaultState.nameTextFieldState,
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
            label = { Text(text = stringResource(R.string.vault_name)) },
            placeholder = { Text(text = stringResource(R.string.vault_name_placeholder)) },
            leadingIcon = {
                AnimatedContent(vaultState.icon) { icon ->
                    Icon(
                        imageVector = icon.toImageVector(),
                        contentDescription = null
                    )
                }
            },
            isError = vaultState.error != null,
            supportingText = vaultState.error?.let { error ->
                {
                    Text(text = error.message())
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
                    checked = vaultState.icon == icon,
                    onCheckedChange = {
                        onIconClick(icon)
                        focusManager.clearFocus()
                    },
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

@Composable
private fun VaultCreationError.message() = when (this) {
    VaultCreationError.BlankName -> stringResource(R.string.vault_name_blank_error)
    VaultCreationError.WrapFailed -> stringResource(R.string.vault_creation_wrap_failed)
}

@Preview
@Composable
private fun VaultCreationDialogContentPreview() {
    KeyGoTheme {
        Surface {
            VaultCreationDialogContent(
                vaultState = VaultState.Create(
                    error = VaultCreationError.BlankName,
                ),
                onIconClick = {},
            )
        }
    }
}

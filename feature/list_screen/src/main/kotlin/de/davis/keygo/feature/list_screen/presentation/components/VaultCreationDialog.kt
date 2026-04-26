package de.davis.keygo.feature.list_screen.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import de.davis.keygo.feature.list_screen.R
import de.davis.keygo.feature.list_screen.presentation.model.CreateVaultRequest


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultCreationDialog(
    onDismissRequest: () -> Unit,
    onCreate: (CreateVaultRequest) -> Unit,
) {
    val textFieldState = rememberTextFieldState()
    var icon by rememberSaveable { mutableStateOf(Vault.Icon.Default) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        CreateVaultRequest(
                            name = textFieldState.text.toString(),
                            icon = icon
                        )
                    )
                }
            ) {
                Text(text = stringResource(R.string.create))
            }
        },
        title = {
            Text(text = stringResource(R.string.create_new_vault))
        },
        text = {
            VaultCreationDialogContent(
                textFieldState = textFieldState,
                selectedIcon = icon,
                onIconClick = { icon = it },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VaultCreationDialogContent(
    textFieldState: TextFieldState,
    selectedIcon: Vault.Icon,
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

@Preview
@Composable
private fun VaultCreationDialogContentPreview() {
    KeyGoTheme {
        Surface {
            VaultCreationDialogContent(
                textFieldState = rememberTextFieldState(),
                selectedIcon = Vault.Icon.Default,
                onIconClick = {},
            )
        }
    }
}

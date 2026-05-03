package de.davis.keygo.feature.vault.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.vault.R
import de.davis.keygo.feature.vault.presentation.model.VaultDeletionError
import de.davis.keygo.feature.vault.presentation.model.VaultState

@Composable
fun VaultDeletionDialog(
    vaultState: VaultState.Delete,
    onConfirmDeletion: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val vaultNameTextFieldState = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.permanently_delete))
        },
        confirmButton = {
            Button(
                onClick = onDismissRequest
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null
            )
        },
        modifier = Modifier.fillMaxWidth(),
        dismissButton = {
            TextButton(
                onClick = { onConfirmDeletion(vaultNameTextFieldState.text.toString()) }
            ) {
                Text(text = stringResource(R.string.delete))
            }
        },
        text = {
            VaultDeletionDialogContent(
                vaultState = vaultState,
                vaultNameTextFieldState = vaultNameTextFieldState,
            )
        },
    )
}

private const val ICON_MARKER = "\uE000" // Private Use Area, won't collide with real text
private const val ICON_ID = "vault_icon"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VaultDeletionDialogContent(
    vaultState: VaultState.Delete,
    vaultNameTextFieldState: TextFieldState
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = AlertDialogDefaults.containerColor,
            ),
            headlineContent = {
                Text(text = vaultState.vaultMetadata.name)
            },
            supportingContent = {
                Text(
                    text = pluralStringResource(
                        R.plurals.vault_item_entry_count,
                        vaultState.vaultMetadata.count,
                        vaultState.vaultMetadata.count
                    )
                )
            },
            leadingContent = {
                Icon(
                    imageVector = vaultState.vaultMetadata.icon.toImageVector(),
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        val rawHtml = pluralStringResource(
            R.plurals.delete_vault_warning,
            vaultState.vaultMetadata.count,
            "$ICON_MARKER ${vaultState.vaultMetadata.name}",
            vaultState.vaultMetadata.count,
        )
        val parsed = AnnotatedString.fromHtml(rawHtml)

        val annotated = remember(parsed) {
            val idx = parsed.text.indexOf(ICON_MARKER)
            if (idx < 0) parsed
            else buildAnnotatedString {
                append(parsed.subSequence(0, idx))
                appendInlineContent(ICON_ID, "[icon]")
                append(parsed.subSequence(idx + ICON_MARKER.length, parsed.length))
            }
        }

        val inlineContent = mapOf(
            ICON_ID to InlineTextContent(
                Placeholder(
                    width = LocalTextStyle.current.fontSize,
                    height = LocalTextStyle.current.fontSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                Icon(
                    imageVector = vaultState.vaultMetadata.icon.toImageVector(),
                    contentDescription = null,
                )
            },
        )

        Text(text = annotated, inlineContent = inlineContent)

        Text(
            text = AnnotatedString.fromHtml(
                stringResource(
                    R.string.delete_instruction,
                    vaultState.vaultMetadata.name
                )
            )
        )

        OutlinedTextField(
            state = vaultNameTextFieldState,
            label = {
                Text(text = stringResource(R.string.vault_name))
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
            isError = vaultState.error != null,
            supportingText = vaultState.error?.let { error ->
                {
                    Text(text = error.toText())
                }
            }
        )
    }
}

@Composable
private fun VaultDeletionError.toText() = when (this) {
    VaultDeletionError.NameDoesNotMatch -> stringResource(R.string.name_does_not_match)
}

@Preview
@Composable
private fun VaultDeletionDialogContentPreview() {
    KeyGoTheme {
        Surface(
            color = AlertDialogDefaults.containerColor
        ) {
            VaultDeletionDialogContent(
                vaultState = VaultState.Delete(
                    vaultMetadata = VaultMetadata(
                        vaultId = newVaultId(),
                        name = "Test Vault",
                        icon = Vault.Icon.Default,
                        count = 3,
                    )
                ),
                vaultNameTextFieldState = rememberTextFieldState()
            )
        }
    }
}
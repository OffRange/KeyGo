package de.davis.keygo.feature.item.create.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.item.core.presentation.component.ChipFormGroup
import de.davis.keygo.feature.item.core.presentation.component.ChipFormMode
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormField
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.model.VaultsState
import de.davis.keygo.feature.item.core.R as ItemCoreR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeyGoItemForm(
    nameTextFieldState: TextFieldState,
    tagsTextFieldState: TextFieldState,
    notesTextFieldState: TextFieldState,
    vaultsState: VaultsState?,
    onVaultSelect: (VaultId) -> Unit,
    assignedTags: Set<Tag>,
    tagsForSuggestions: Set<Tag>,
    onTagSubmitted: (Set<Tag>) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
    nameError: InputFieldError? = null,
    nameExists: Boolean = false,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "general_information") {
            FormGroup(
                title = stringResource(R.string.general_information),
            ) {
                Column {
                    AnimatedVisibility(
                        visible = nameExists,
                    ) {
                        KeyGoCard(
                            title = {
                                Text(text = stringResource(R.string.warning))
                            },
                            properties = KeyGoCardProperties.elevated(containerColor = MaterialTheme.colorScheme.secondaryContainer),

                            // We use a padding modifier and not a verticalArrangement, as verticalArrangement
                            // causes the layout to snap when disappearing. That is also why we use
                            // a separate Column as the default Column of the form has the
                            // verticalArrangement attribute set.
                            modifier = Modifier.padding(bottom = 8.dp),
                            leadingItem = {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                )
                            },
                        ) {
                            Text(text = stringResource(R.string.multiple_items_same_name))
                        }
                    }

                    KeyGoFormField(
                        state = nameTextFieldState,
                        label = { Text(text = stringResource(ItemCoreR.string.name)) },
                        placeholder = { Text(text = stringResource(R.string.name_placeholder)) },
                        error = nameError
                    )

                    // We only show the vault selection when there are more then one vaults available
                    vaultsState?.takeIf { it.vaults.size > 1 }?.let { state ->
                        Spacer(modifier = Modifier.height(8.dp))
                        VaultDropDownMenu(
                            vaultsState = state,
                            onVaultSelect = onVaultSelect,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        content()

        item(key = "tags") {
            ChipFormGroup(
                title = stringResource(R.string.tag_information),
                items = assignedTags,
                textOf = { it },
                containsForInput = {
                    assignedTags.any { tag ->
                        tag.equals(it, ignoreCase = true)
                    }
                },
                onSubmit = { onTagSubmitted(it) },
                onDelete = { onDeleteTag(it) },
                label = {
                    Text(text = stringResource(R.string.add_tags))
                },
                state = tagsTextFieldState,
                delimiters = TAG_DELIMITERS,
                mode = ChipFormMode.WithSuggestions(suggestions = tagsForSuggestions)
            )
        }

        item(key = "notes") {
            FormGroup(
                title = stringResource(R.string.additional_information),
                modifier = Modifier
            ) {
                KeyGoFormField(
                    state = notesTextFieldState,
                    label = { Text(text = stringResource(ItemCoreR.string.note)) },
                    placeholder = { Text(text = stringResource(R.string.note_placeholder)) },
                    lineLimits = TextFieldLineLimits.MultiLine(
                        minHeightInLines = 3,
                        maxHeightInLines = 5
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done
                    )
                )
            }
        }
    }
}

@Composable
internal fun FormGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    KeyGoCard(
        title = {
            Text(text = title)
        },
        modifier = modifier,
        properties = KeyGoCardProperties.elevated(),
        content = content
    )
}

internal val TAG_DELIMITERS = setOf(',')

@Preview
@Composable
private fun KeyGoItemFormPreview() {
    KeyGoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val state = remember { TextFieldState() }

            KeyGoItemForm(
                nameTextFieldState = remember { TextFieldState() },
                notesTextFieldState = remember { TextFieldState() },
                tagsTextFieldState = remember { TextFieldState() },
                vaultsState = remember {
                    val id = newVaultId()
                    VaultsState(
                        vaults = listOf(
                            VaultMetadata(
                                vaultId = newVaultId(),
                                name = "Vault 1",
                                icon = Vault.Icon.Default
                            ),
                            VaultMetadata(
                                vaultId = id,
                                name = "Vault 2",
                                icon = Vault.Icon.Work
                            )
                        ), selectedVaultId = id
                    )
                },
                onVaultSelect = {},
                nameExists = true,
                assignedTags = setOf("Tag 1", "Tag 2", "Tag 3"),
                tagsForSuggestions = emptySet(),
                onTagSubmitted = {},
                onDeleteTag = {},
            ) {
                item(key = "password_information") {
                    FormGroup(
                        title = "Password Information",
                    ) {
                        KeyGoFormField(
                            state = state,
                            label = { Text(text = "Password") },
                            placeholder = { Text(text = "Enter password") },
                            isSecure = true,
                            outsideTrailingContent = {
                                IconButton(
                                    onClick = {}
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null
                                    )
                                }
                            },
                            error = InputFieldError.Empty
                        )

                        KeyGoFormField(
                            state = state,
                            label = { Text(text = "Username") },
                            placeholder = { Text(text = "Enter username") }
                        )

                        KeyGoFormField(
                            state = state,
                            label = { Text(text = "Website") },
                            placeholder = { Text(text = "Enter Website") }
                        )
                    }
                }
            }
        }
    }
}
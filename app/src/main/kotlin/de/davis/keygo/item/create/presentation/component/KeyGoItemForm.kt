package de.davis.keygo.item.create.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.WarningAmber
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
import de.davis.keygo.R
import de.davis.keygo.core.presentation.component.KeyGoCard
import de.davis.keygo.core.presentation.component.KeyGoCardProp
import de.davis.keygo.core.presentation.component.KeyGoFormField
import de.davis.keygo.core.presentation.model.InputFieldError


@Composable
fun KeyGoItemForm(
    nameTextFieldState: TextFieldState,
    notesTextFieldState: TextFieldState,
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
                            prop = KeyGoCardProp.elevated(containerColor = MaterialTheme.colorScheme.secondaryContainer),

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
                        label = { Text(text = stringResource(R.string.name)) },
                        placeholder = { Text(text = stringResource(R.string.name_placeholder)) },
                        error = nameError
                    )
                }
            }
        }

        content()

        item(key = "additional_information") {
            FormGroup(
                title = stringResource(R.string.additional_information),
                modifier = Modifier
            ) {
                KeyGoFormField(
                    state = notesTextFieldState,
                    label = { Text(text = stringResource(R.string.note)) },
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
        prop = KeyGoCardProp.elevated(),
        content = content
    )
}

@Preview
@Composable
private fun KeyGoItemFormPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val state = remember { TextFieldState() }

            KeyGoItemForm(
                nameTextFieldState = remember { TextFieldState() },
                notesTextFieldState = remember { TextFieldState() },
                nameExists = true
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
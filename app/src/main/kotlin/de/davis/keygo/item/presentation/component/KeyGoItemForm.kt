package de.davis.keygo.item.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davis.keygo.R
import de.davis.keygo.core.presentation.component.VisibilityButton


@Composable
fun KeyGoItemForm(
    nameTextFieldState: TextFieldState,
    notesTextFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "general_information") {
            FormGroup(
                title = stringResource(R.string.general_information),
                modifier = Modifier
            ) {
                FormField(
                    state = nameTextFieldState,
                    label = { Text(text = stringResource(R.string.name)) },
                    placeholder = { Text(text = stringResource(R.string.name_placeholder)) }
                )
            }
        }

        content()

        item(key = "additional_information") {
            FormGroup(
                title = stringResource(R.string.additional_information),
                modifier = Modifier
            ) {
                FormField(
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
    ElevatedCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
            )

            content()
        }
    }
}

@Composable
internal fun FormField(
    state: TextFieldState,
    label: @Composable TextFieldLabelScope.() -> Unit,
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    outsideTrailingContent: @Composable (() -> Unit)? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (isSecure) {
            true -> {
                var passwordHidden by rememberSaveable { mutableStateOf(true) }
                OutlinedSecureTextField(
                    state = state,
                    modifier = Modifier.weight(1f),
                    label = label,
                    placeholder = placeholder,
                    textObfuscationMode = if (passwordHidden) {
                        TextObfuscationMode.RevealLastTyped
                    } else {
                        TextObfuscationMode.Visible
                    },
                    trailingIcon = {
                        VisibilityButton(
                            isHidden = passwordHidden,
                            onClick = { passwordHidden = !passwordHidden },
                        )
                    },
                    keyboardOptions = keyboardOptions
                )
            }

            else -> OutlinedTextField(
                state = state,
                modifier = Modifier.weight(1f),
                label = label,
                placeholder = placeholder,
                trailingIcon = trailingContent,
                lineLimits = lineLimits,
                keyboardOptions = keyboardOptions
            )
        }

        outsideTrailingContent?.let {
            Box(modifier = Modifier.padding(top = minimizedLabelHalfHeight())) {
                it()
            }
        }
    }
}

/**
 * Copied from android's internal source code
 * Source: OutlinedTextField padding
 */
@Composable
internal fun minimizedLabelHalfHeight(): Dp {
    val compositionLocalValue = MaterialTheme.typography.bodySmall.lineHeight
    val fallbackValue = 16.sp
    val value = if (compositionLocalValue.isSp) compositionLocalValue else fallbackValue
    return with(LocalDensity.current) { value.toDp() / 2 }
}

@Preview
@Composable
private fun KeyGoItemFormPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val state = remember { TextFieldState() }

            KeyGoItemForm(
                nameTextFieldState = remember { TextFieldState() },
                notesTextFieldState = remember { TextFieldState() }
            ) {
                item(key = "password_information") {
                    FormGroup(
                        title = "Password Information",
                    ) {
                        FormField(
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
                            }
                        )

                        FormField(
                            state = state,
                            label = { Text(text = "Username") },
                            placeholder = { Text(text = "Enter username") }
                        )

                        FormField(
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
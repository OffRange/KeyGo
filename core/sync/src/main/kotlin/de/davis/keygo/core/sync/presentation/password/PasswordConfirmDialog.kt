package de.davis.keygo.core.sync.presentation.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.davis.keygo.core.sync.R
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.presentation.messageRes
import de.davis.keygo.core.ui.components.VisibilityButton

@Composable
fun PasswordConfirmDialog(
    password: TextFieldState,
    busy: Boolean,
    error: SyncError?,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    message: @Composable () -> Unit,
    confirmButton: @Composable (enabled: Boolean, onClick: () -> Unit) -> Unit,
) {
    val hasText by remember(password) { derivedStateOf { password.text.isNotEmpty() } }
    val canSubmit = !busy && hasText
    val submit = {
        if (canSubmit) onSubmit()
    }

    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
        ),
        confirmButton = { confirmButton(canSubmit, submit) },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        icon = icon,
        title = title,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message()

                var hidden by rememberSaveable { mutableStateOf(true) }
                OutlinedSecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text(text = stringResource(R.string.step_up_password)) },
                    isError = error != null,
                    textObfuscationMode =
                        if (hidden) TextObfuscationMode.RevealLastTyped
                        else TextObfuscationMode.Visible,
                    trailingIcon = {
                        VisibilityButton(isHidden = hidden, onClick = { hidden = !hidden })
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { submit() },
                )

                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                error?.let {
                    Text(
                        text = stringResource(it.messageRes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Preview
@Composable
private fun PasswordConfirmDialogPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            PasswordConfirmDialog(
                password = TextFieldState(),
                busy = false,
                error = SyncError.InvalidCredentials,
                onSubmit = {},
                onCancel = {},
                icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
                title = { Text(text = "Confirm it's you") },
                message = { Text(text = "Enter your account password to continue.") },
                confirmButton = { enabled, onClick ->
                    TextButton(onClick = onClick, enabled = enabled) {
                        Text(text = "Confirm")
                    }
                },
            )
        }
    }
}

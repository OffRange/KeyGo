package de.davis.keygo.feature.account.presentation.stepup

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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompt
import de.davis.keygo.core.sync.presentation.messageRes
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.account.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun StepUpDialogHost() {
    val viewModel = koinViewModel<StepUpViewModel>()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()

    prompt?.let {
        StepUpDialog(
            prompt = it,
            password = viewModel.password,
            onSubmit = viewModel::onSubmit,
            onCancel = viewModel::onCancel,
        )
    }
}

@Composable
internal fun StepUpDialog(
    prompt: StepUpPrompt,
    password: TextFieldState,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val busy = prompt.busy
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
        confirmButton = {
            TextButton(onClick = submit, enabled = canSubmit) {
                Text(text = stringResource(R.string.step_up_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        icon = {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.step_up_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.step_up_message))

                var hidden by rememberSaveable { mutableStateOf(true) }
                OutlinedSecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text(text = stringResource(R.string.step_up_password)) },
                    isError = prompt.error != null,
                    textObfuscationMode =
                        if (hidden) TextObfuscationMode.RevealLastTyped
                        else TextObfuscationMode.Visible,
                    trailingIcon = {
                        VisibilityButton(isHidden = hidden, onClick = { hidden = !hidden })
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { submit() },
                )

                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                prompt.error?.let {
                    Text(
                        text = stringResource(it.messageRes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

private class StepUpPromptProvider : PreviewParameterProvider<StepUpPrompt> {

    override val values = sequenceOf(
        StepUpPrompt(),
        StepUpPrompt(busy = true),
        StepUpPrompt(error = SyncError.InvalidCredentials),
    )
}

@Preview
@Composable
private fun StepUpDialogPreview(
    @PreviewParameter(StepUpPromptProvider::class) prompt: StepUpPrompt,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            StepUpDialog(
                prompt = prompt,
                password = TextFieldState(),
                onSubmit = {},
                onCancel = {},
            )
        }
    }
}

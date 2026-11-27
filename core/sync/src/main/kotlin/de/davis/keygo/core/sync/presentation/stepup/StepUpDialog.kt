package de.davis.keygo.core.sync.presentation.stepup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.sync.R
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompt
import de.davis.keygo.core.sync.presentation.password.PasswordConfirmDialog
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
    PasswordConfirmDialog(
        password = password,
        busy = prompt.busy,
        error = prompt.error,
        onSubmit = onSubmit,
        onCancel = onCancel,
        icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
        title = { Text(text = stringResource(R.string.step_up_title)) },
        message = { Text(text = stringResource(R.string.step_up_message)) },
        confirmButton = { enabled, onClick ->
            TextButton(onClick = onClick, enabled = enabled) {
                Text(text = stringResource(R.string.step_up_confirm))
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

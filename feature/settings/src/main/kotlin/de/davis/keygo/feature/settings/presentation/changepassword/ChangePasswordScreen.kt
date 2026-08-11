package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.BiometricString
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.ui.model.error
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.core.util.presentation.snackbar.LocalSnackbarManager
import de.davis.keygo.feature.settings.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun ChangePasswordScreen(onUp: () -> Unit) {
    val viewModel = koinViewModel<ChangePasswordViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val controller = rememberBiometricCryptoController()
    val scope = rememberCoroutineScope()
    val snackbarManager = LocalSnackbarManager.current

    ObserveAsEvents(viewModel.event) { event ->
        when (event) {
            ChangePasswordEvent.Success -> onUp()
            ChangePasswordEvent.GenericError -> snackbarManager.sendMessage(
                SnackbarMessage(message = ResourceString(R.string.change_password_failed)),
            )

            ChangePasswordEvent.LaunchBiometricPrompt -> {
                val ciphertext = state.biometricCiphertext ?: return@ObserveAsEvents
                scope.launch {
                    val result = controller.requestUnwrap(
                        keyId = KeyId.BiometricVaultKek,
                        ciphertextData = ciphertext,
                        policy = BiometricPolicy(
                            negativeButton = BiometricString.NegativeButton.Password,
                        ),
                    )
                    viewModel.onBiometricResult(result)
                }
            }
        }
    }

    ChangePasswordContent(
        state = state,
        onUp = onUp,
        onSubmit = viewModel::onSubmit,
        onSubmitWithPassword = viewModel::submitWithPassword,
        onDismissReauthDialog = viewModel::dismissReauthDialog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangePasswordContent(
    state: ChangePasswordState,
    onUp: () -> Unit,
    onSubmit: () -> Unit,
    onSubmitWithPassword: () -> Unit,
    onDismissReauthDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.change_password_title)) },
                    navigationIcon = {
                        IconButton(onClick = onUp, enabled = !state.loading) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(
                    visible = state.loading && !state.showReauthDialog,
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.canUseBiometric) CurrentPasswordField(
                state = state.currentPassword,
                error = state.currentPasswordError,
            )

            var newHidden by rememberSaveable { mutableStateOf(true) }
            var forceCompact by rememberSaveable { mutableStateOf(false) }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedSecureTextField(
                    state = state.newPassword,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            forceCompact = !it.isFocused
                        },
                    label = { Text(stringResource(R.string.new_password)) },
                    isError = state.newPasswordError != null,
                    supportingText = state.newPasswordError?.let {
                        { Text(text = it.error) }
                    },
                    textObfuscationMode = obfuscation(newHidden),
                    trailingIcon = {
                        VisibilityButton(
                            isHidden = newHidden,
                            onClick = { newHidden = !newHidden },
                        )
                    },
                )

                StrengthIndicator(
                    passwordScore = state.passwordScore,
                    forceCompact = forceCompact
                )
            }

            var confirmHidden by rememberSaveable { mutableStateOf(true) }
            OutlinedSecureTextField(
                state = state.confirmPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.confirm_password)) },
                isError = state.confirmPasswordError != null,
                supportingText = state.confirmPasswordError?.let {
                    { Text(text = it.error) }
                },
                textObfuscationMode = obfuscation(confirmHidden),
                trailingIcon = {
                    VisibilityButton(
                        isHidden = confirmHidden,
                        onClick = { confirmHidden = !confirmHidden },
                    )
                },
            )

            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            ) {
                if (state.canUseBiometric) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(stringResource(R.string.change_password_action))
            }

            if (state.canUseBiometric) Text(
                text = stringResource(R.string.biometric_confirm_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.showReauthDialog) {
        AlertDialog(
            onDismissRequest = onDismissReauthDialog,
            title = { Text(stringResource(R.string.reauth_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CurrentPasswordField(
                        state = state.currentPassword,
                        error = state.currentPasswordError,
                    )
                    AnimatedVisibility(
                        visible = state.loading,
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(onClick = onSubmitWithPassword, enabled = !state.loading) {
                    Text(stringResource(R.string.change_password_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReauthDialog, enabled = !state.loading) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private fun obfuscation(hidden: Boolean): TextObfuscationMode =
    if (hidden) TextObfuscationMode.RevealLastTyped else TextObfuscationMode.Visible

@Composable
private fun CurrentPasswordField(
    state: TextFieldState,
    error: UiFieldError?,
    modifier: Modifier = Modifier,
) {
    var hidden by rememberSaveable { mutableStateOf(true) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.current_password)) },
        isError = error != null,
        supportingText = error?.let {
            { Text(text = it.error) }
        },
        textObfuscationMode = obfuscation(hidden),
        trailingIcon = {
            VisibilityButton(isHidden = hidden, onClick = { hidden = !hidden })
        },
    )
}

private class ChangePasswordStateProvider : PreviewParameterProvider<ChangePasswordState> {


    private val previewBiometricCiphertext = CiphertextData(bytes = ByteArray(0), iv = ByteArray(0))

    override val values = sequenceOf(
        ChangePasswordState(),
        ChangePasswordState(biometricCiphertext = previewBiometricCiphertext),
        ChangePasswordState(
            currentPasswordError = UiFieldError.Incorrect,
            newPasswordError = UiFieldError.Empty,
            confirmPasswordError = UiFieldError.Mismatch,
        ),
        ChangePasswordState(loading = true),
        ChangePasswordState(
            biometricCiphertext = previewBiometricCiphertext,
            showReauthDialog = true,
        ),
        ChangePasswordState(
            biometricCiphertext = previewBiometricCiphertext,
            showReauthDialog = true,
            loading = true,
        ),
    )
}

@Preview
@Composable
private fun ChangePasswordPreviewContainer(
    @PreviewParameter(ChangePasswordStateProvider::class) state: ChangePasswordState
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            ChangePasswordContent(
                state = state,
                onUp = {},
                onSubmit = {},
                onSubmitWithPassword = {},
                onDismissReauthDialog = {},
            )
        }
    }
}

package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.BiometricString
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.settings.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangePasswordScreen(onUp: () -> Unit) {
    val viewModel = koinViewModel<ChangePasswordViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val controller = rememberBiometricCryptoController()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.event) { event ->
        when (event) {
            ChangePasswordEvent.Success -> onUp()
            ChangePasswordEvent.GenericError -> Unit // surfaced inline; snackbar wiring is a follow-up
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_password_title)) },
                navigationIcon = {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.canUseBiometric) CurrentPasswordField(
                state = state.currentPassword,
                error = state.currentPasswordError,
            )

            var newHidden by rememberSaveable { mutableStateOf(true) }
            OutlinedSecureTextField(
                state = state.newPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.new_password)) },
                isError = state.newPasswordError !is FieldError.None,
                supportingText = supportingTextFor(state.newPasswordError),
                textObfuscationMode = obfuscation(newHidden),
                trailingIcon = {
                    VisibilityButton(
                        isHidden = newHidden,
                        onClick = { newHidden = !newHidden },
                    )
                },
            )

            StrengthIndicator(passwordScore = state.passwordScore)

            var confirmHidden by rememberSaveable { mutableStateOf(true) }
            OutlinedSecureTextField(
                state = state.confirmPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.confirm_password)) },
                isError = state.confirmPasswordError !is FieldError.None,
                supportingText = supportingTextFor(state.confirmPasswordError),
                textObfuscationMode = obfuscation(confirmHidden),
                trailingIcon = {
                    VisibilityButton(
                        isHidden = confirmHidden,
                        onClick = { confirmHidden = !confirmHidden },
                    )
                },
            )

            Button(
                onClick = { viewModel.onSubmit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                if (state.canUseBiometric) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
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
            onDismissRequest = { viewModel.dismissReauthDialog() },
            title = { Text(stringResource(R.string.reauth_dialog_title)) },
            text = { CurrentPasswordField(state = state.currentPassword, error = state.currentPasswordError) },
            confirmButton = {
                TextButton(onClick = { viewModel.submitWithPassword() }, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.change_password_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissReauthDialog() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

private fun obfuscation(hidden: Boolean): TextObfuscationMode =
    if (hidden) TextObfuscationMode.RevealLastTyped else TextObfuscationMode.Visible

@Composable
private fun supportingTextFor(error: FieldError): (@Composable () -> Unit)? = when (error) {
    FieldError.None -> null
    FieldError.Empty -> { { Text(stringResource(R.string.password_blank)) } }
    FieldError.Incorrect -> { { Text(stringResource(R.string.incorrect_password)) } }
    FieldError.Mismatch -> { { Text(stringResource(R.string.passwords_do_not_match)) } }
}

@Composable
private fun CurrentPasswordField(
    state: TextFieldState,
    error: FieldError,
    modifier: Modifier = Modifier,
) {
    var hidden by rememberSaveable { mutableStateOf(true) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.current_password)) },
        isError = error !is FieldError.None,
        supportingText = supportingTextFor(error),
        textObfuscationMode = obfuscation(hidden),
        trailingIcon = {
            VisibilityButton(isHidden = hidden, onClick = { hidden = !hidden })
        },
    )
}

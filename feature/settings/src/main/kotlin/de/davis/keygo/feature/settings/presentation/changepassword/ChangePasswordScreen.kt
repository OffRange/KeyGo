package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.core.util.Result
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
            var currentHidden by rememberSaveable { mutableStateOf(true) }
            OutlinedSecureTextField(
                state = state.currentPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.current_password)) },
                isError = state.currentPasswordError !is FieldError.None,
                supportingText = supportingTextFor(state.currentPasswordError),
                textObfuscationMode = obfuscation(currentHidden),
                trailingIcon = {
                    VisibilityButton(
                        isHidden = currentHidden,
                        onClick = { currentHidden = !currentHidden },
                    )
                },
            )

            if (state.canUseBiometric) {
                OutlinedButton(
                    onClick = {
                        val ciphertext = state.biometricCiphertext ?: return@OutlinedButton
                        scope.launch {
                            when (val r = controller.requestUnwrap(KeyId.BiometricVaultKek, ciphertext)) {
                                is Result.Success -> viewModel.submitWithBiometric(r.success.encoded)
                                is Result.Failure -> Unit // user cancelled / failed
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Text(
                        text = stringResource(R.string.verify_with_biometric),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

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
                onClick = { viewModel.submitWithPassword() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading,
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.change_password_action))
            }
        }
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

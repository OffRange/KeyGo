package de.davis.keygo.feature.auth.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.auth.R
import de.davis.keygo.feature.auth.presentation.model.AuthState
import de.davis.keygo.feature.auth.presentation.model.AuthUIEvent
import de.davis.keygo.feature.auth.presentation.model.UIPasswordError
import de.davis.keygo.core.item.R as CoreItemR

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthContent(state: AuthState, onEvent: (AuthUIEvent) -> Unit) {
    when (state) {
        is AuthState.Loading -> {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ContainedLoadingIndicator()
                }
            }
        }

        is AuthState.Interactable -> InteractableAuthContent(state = state, onEvent = onEvent)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun InteractableAuthContent(
    state: AuthState.Interactable,
    onEvent: (AuthUIEvent) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            ElevatedCard(modifier = Modifier.widthIn(max = 500.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(state.firstTitlePart)
                            append(" ")

                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSynthesis = FontSynthesis.All,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary,
                                            MaterialTheme.colorScheme.tertiary,
                                        )
                                    )
                                )
                            ) {
                                append(stringResource(R.string.your_vault))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )

                    with(state) {
                        var passwordHidden by rememberSaveable { mutableStateOf(true) }
                        var forceCompact by rememberSaveable { mutableStateOf(false) }
                        OutlinedSecureTextField(
                            state = passwordTextFieldState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    forceCompact = !it.hasFocus
                                },
                            label = {
                                Text(text = stringResource(CoreItemR.string.password))
                            },
                            isError = passwordError !is UIPasswordError.None,
                            supportingText = when (passwordError) {
                                is UIPasswordError.None -> null
                                is UIPasswordError.Incorrect -> {
                                    { Text(stringResource(R.string.incorrect_password)) }
                                }

                                else -> {
                                    { Text(stringResource(R.string.blank_password)) }
                                }
                            },
                            textObfuscationMode = when {
                                passwordHidden -> TextObfuscationMode.RevealLastTyped
                                else -> TextObfuscationMode.Visible
                            },
                            trailingIcon = {
                                VisibilityButton(
                                    isHidden = passwordHidden,
                                    onClick = { passwordHidden = !passwordHidden },
                                )
                            },
                        )

                        if (this is AuthState.CreateAccess) {
                            StrengthIndicator(
                                score = score,
                                forceCompact = forceCompact,
                            )

                            var confirmPasswordHidden by rememberSaveable { mutableStateOf(true) }
                            OutlinedSecureTextField(
                                state = confirmPasswordTextFieldState,
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(text = stringResource(R.string.confirm_password))
                                },
                                isError = confirmPasswordError !is UIPasswordError.None,
                                supportingText = when (confirmPasswordError) {
                                    is UIPasswordError.None -> null
                                    is UIPasswordError.Incorrect -> {
                                        { Text(stringResource(R.string.password_does_not_match)) }
                                    }

                                    else -> {
                                        { Text(stringResource(R.string.blank_password)) }
                                    }
                                },
                                textObfuscationMode = when {
                                    confirmPasswordHidden -> TextObfuscationMode.RevealLastTyped
                                    else -> TextObfuscationMode.Visible
                                },
                                trailingIcon = {
                                    VisibilityButton(
                                        isHidden = confirmPasswordHidden,
                                        onClick = {
                                            confirmPasswordHidden = !confirmPasswordHidden
                                        },
                                    )
                                },
                            )
                        }

                        if (state is AuthState.BiometricAuthState && state.biometricsAvailable)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = stringResource(R.string.use_biometrics))

                                Switch(
                                    checked = state.useBiometrics,
                                    onCheckedChange = {
                                        onEvent(AuthUIEvent.ToggleUseBiometrics(it))
                                    }
                                )
                            }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onEvent(AuthUIEvent.Submit) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.loading
                        ) {
                            Text(text = state.buttonText)
                        }

                        if (state is AuthState.Login && state.biometricAuthenticationAvailable) {
                            FilledTonalIconButton(
                                onClick = {
                                    onEvent(AuthUIEvent.RequestBiometricAuthentication)
                                },
                                enabled = !state.loading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = stringResource(R.string.request_biometric_authentication_content_description)
                                )
                            }
                        }
                    }
                }
            }

            if (state.loading) {
                BasicAlertDialog(
                    onDismissRequest = { },
                ) {
                    Box(
                        modifier = Modifier.sizeIn(
                            minWidth = DialogMinWidth,
                            maxWidth = DialogMaxWidth
                        ),
                        propagateMinConstraints = true
                    ) {
                        LoadingIndicator()
                    }
                }
            }

            if (state is AuthState.Migrating && state.showMigrationDialog)
                MigrationDialog(onClick = { onEvent(AuthUIEvent.CloseMigrationDialog) })
        }
    }
}

@Composable
fun MigrationDialog(onClick: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = null
            )
        },
        title = {
            Text(text = stringResource(R.string.migrating))
        },
        text = {
            Text(text = stringResource(R.string.migrate_to_access_description))
        },
        confirmButton = {
            Button(
                onClick = onClick
            ) {
                Text(text = stringResource(R.string.migrate))
            }
        }
    )
}

internal val DialogMinWidth = 280.dp
internal val DialogMaxWidth = 560.dp

private val AuthState.Interactable.firstTitlePart: String
    @Composable
    get() = when (this) {
        is AuthState.Login -> stringResource(R.string.authenticate_to_access)
        is AuthState.Migrating -> stringResource(R.string.migrate_to_access)
        is AuthState.CreateAccess -> stringResource(R.string.create_access_access)
    }

private val AuthState.Interactable.buttonText: String
    @Composable
    get() = when (this) {
        is AuthState.Login -> stringResource(R.string.authenticate)
        is AuthState.Migrating -> stringResource(R.string.migrate)
        is AuthState.CreateAccess -> stringResource(R.string.create_access)
    }

@Composable
@Preview
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
private fun AuthContentPreview() {
    KeyGoTheme {
        AuthContent(
            state = AuthState.CreateAccess(
                passwordTextFieldState = TextFieldState(),
                biometricsAvailable = true
            ),
            onEvent = {}
        )
    }
}
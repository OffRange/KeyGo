package de.davis.keygo.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.auth.presentation.model.AuthState
import de.davis.keygo.auth.presentation.model.AuthUIEvent
import de.davis.keygo.auth.presentation.model.UIPasswordError
import de.davis.keygo.core.presentation.theme.KeyGoTheme

@Composable
fun AuthContent(state: AuthState, onEvent: (AuthUIEvent) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            ElevatedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.authenticate_to_access))
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
                        }
                    )

                    var passwordHidden by rememberSaveable { mutableStateOf(true) }
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onEvent(AuthUIEvent.PasswordChanged(it)) },
                        label = { Text(stringResource(R.string.password)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordHidden) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordHidden = !passwordHidden }
                            ) {
                                val icon =
                                    if (passwordHidden) Icons.Default.Visibility
                                    else Icons.Default.VisibilityOff

                                val description =
                                    if (passwordHidden) R.string.show_password_content_description
                                    else R.string.hide_password_content_description
                                Icon(
                                    imageVector = icon,
                                    contentDescription = stringResource(id = description)
                                )
                            }
                        },
                        isError = state.passwordError !is UIPasswordError.None,
                        supportingText = when (state.passwordError) {
                            is UIPasswordError.None -> null
                            is UIPasswordError.Incorrect -> {
                                { Text(stringResource(R.string.incorrect_password)) }
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onEvent(AuthUIEvent.Submit) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.authenticate))
                        }

                        if (state.biometricsAvailable) {
                            FilledTonalIconButton(
                                onClick = {
                                    onEvent(AuthUIEvent.RequestBiometricAuthentication)
                                }
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
        }
    }
}

@Composable
@Preview
@Preview(
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
private fun AuthContentPreview() {
    KeyGoTheme {
        AuthContent(
            state = AuthState(biometricsAvailable = true),
            onEvent = {}
        )
    }
}
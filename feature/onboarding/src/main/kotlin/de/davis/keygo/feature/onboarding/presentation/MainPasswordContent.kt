package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.component.OnboardingScaffold
import de.davis.keygo.feature.onboarding.presentation.component.SmallIconContainer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MainPasswordContent(
    onContinue: () -> Unit
) {
    OnboardingScaffold(
        iconContainer = {
            SmallIconContainer(
                shape = MaterialShapes.Diamond.toShape()
            ) {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = null
                )
            }
        },
        title = stringResource(R.string.main_password_title),
        description = stringResource(R.string.main_password_subtitle),
        buttonText = stringResource(R.string.continue_text),
        onButtonClicked = onContinue,
        info = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.main_password_info),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    ) {
        var forceCompact by rememberSaveable { mutableStateOf(false) }
        var passwordHidden by remember { mutableStateOf(true) }
        OutlinedSecureTextField(
            state = passwordState,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    forceCompact = !it.hasFocus
                },
            label = { Text(text = stringResource(R.string.main_password_label)) },
            textObfuscationMode = if (passwordHidden) TextObfuscationMode.RevealLastTyped
            else TextObfuscationMode.Visible,
            trailingIcon = {
                VisibilityButton(
                    isHidden = passwordHidden,
                    onClick = { passwordHidden = !passwordHidden }
                )
            }
        )

        StrengthIndicator(
            passwordScore = PasswordScore.Moderate,
            forceCompact = forceCompact,
        )

        var confirmPasswordHidden by remember { mutableStateOf(true) }
        OutlinedSecureTextField(
            state = passwordState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.confirm_main_password_label)) },
            textObfuscationMode = if (confirmPasswordHidden) TextObfuscationMode.RevealLastTyped
            else TextObfuscationMode.Visible,
            trailingIcon = {
                VisibilityButton(
                    isHidden = confirmPasswordHidden,
                    onClick = { confirmPasswordHidden = !confirmPasswordHidden }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun MainPasswordContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    MainPasswordContent(
                        onContinue = {}
                    )
                }
            }
        }
    }
}
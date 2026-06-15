package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ProvidePassphraseState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProvidePassphraseContent(
    state: ProvidePassphraseState,
    onEvent: (ExportWizardUiEvent) -> Unit
) {
    var passphraseHidden by rememberSaveable { mutableStateOf(true) }
    var confirmPassphraseHidden by rememberSaveable { mutableStateOf(true) }
    var forceCompact by rememberSaveable { mutableStateOf(false) }
    Surface {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.export_passphrase_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PassphraseField(
                    state = state.passphraseTextFieldState,
                    label = stringResource(R.string.passphrase),
                    hidden = passphraseHidden,
                    onToggleHidden = { passphraseHidden = !passphraseHidden },
                    modifier = Modifier.onFocusChanged { forceCompact = !it.hasFocus },
                )
                StrengthIndicator(
                    passwordScore = state.passphraseScore,
                    forceCompact = forceCompact,
                )

                PassphraseField(
                    state = state.confirmPassphraseTextFieldState,
                    label = stringResource(R.string.confirm_passphrase),
                    hidden = confirmPassphraseHidden,
                    onToggleHidden = { confirmPassphraseHidden = !confirmPassphraseHidden },
                )
            }

            ContinueButton(onEvent = onEvent)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PassphraseField(
    state: TextFieldState,
    label: String,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(text = label)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Password,
                contentDescription = null,
            )
        },
        textObfuscationMode = if (hidden) TextObfuscationMode.RevealLastTyped
        else TextObfuscationMode.Visible,
        trailingIcon = {
            VisibilityButton(
                isHidden = hidden,
                onClick = onToggleHidden,
            )
        },
    )
}

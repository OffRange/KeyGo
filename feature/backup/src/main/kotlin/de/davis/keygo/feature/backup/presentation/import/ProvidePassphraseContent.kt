package de.davis.keygo.feature.backup.presentation.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.VisibilityButton
import de.davis.keygo.feature.backup.R

@Composable
internal fun ProvidePassphraseContent(
    passphraseState: TextFieldState,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    var hidden by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.import_passphrase_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedSecureTextField(
            state = passphraseState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.passphrase)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Password, contentDescription = null)
            },
            isError = isError,
            supportingText = if (isError) {
                { Text(text = stringResource(R.string.import_passphrase_error)) }
            } else null,
            textObfuscationMode = if (hidden) TextObfuscationMode.RevealLastTyped
            else TextObfuscationMode.Visible,
            trailingIcon = {
                VisibilityButton(isHidden = hidden, onClick = { hidden = !hidden })
            },
        )
    }
}
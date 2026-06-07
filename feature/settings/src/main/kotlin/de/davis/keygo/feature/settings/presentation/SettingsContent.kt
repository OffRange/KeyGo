package de.davis.keygo.feature.settings.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.settings.R
import de.davis.keygo.feature.settings.presentation.component.SettingsList

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        SettingsList(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = innerPadding,
        ) {
            section(title = R.string.settings_security) {
                action(
                    title = R.string.settings_reset_password,
                    icon = Icons.Default.LockReset,
                    onClick = { onEvent(SettingsUiEvent.ResetPassword) },
                )

                toggle(
                    title = R.string.settings_use_biometrics,
                    icon = Icons.Default.Fingerprint,
                    checked = state.biometricsEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetBiometrics(it)) },
                )

                toggle(
                    title = R.string.settings_autofill,
                    icon = Icons.Default.Password,
                    checked = state.autofillEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetAutofill(it)) },
                )
            }

            section(title = R.string.settings_about) {
                action(
                    title = R.string.settings_3rd_party_licenses,
                    icon = Icons.Default.Code,
                    isNavigation = true,
                    onClick = { onEvent(SettingsUiEvent.LibrariesClicked) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsContentPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            SettingsContent(
                state = SettingsUiState(),
                onEvent = {},
            )
        }
    }
}

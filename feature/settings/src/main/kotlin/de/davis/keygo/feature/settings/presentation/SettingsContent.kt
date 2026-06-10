package de.davis.keygo.feature.settings.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Update
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
                    supporting = R.string.settings_reset_password_description,
                    onClick = { onEvent(SettingsUiEvent.ResetPassword) },
                )

                toggle(
                    title = R.string.settings_use_biometrics,
                    icon = Icons.Default.Fingerprint,
                    supporting = R.string.settings_use_biometrics_description,
                    checked = state.biometricsEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetBiometrics(it)) },
                )

                toggle(
                    title = R.string.settings_autofill,
                    icon = Icons.Default.Password,
                    supporting = R.string.settings_autofill_description,
                    checked = state.autofillEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetAutofill(it)) },
                )
            }

            section(title = R.string.settings_backup) {
                action(
                    title = R.string.settings_export_data,
                    icon = Icons.Default.Backup,
                    supporting = R.string.settings_export_data_description,
                    onClick = { onEvent(SettingsUiEvent.ExportData) },
                )

                action(
                    title = R.string.settings_import_data,
                    icon = Icons.Default.SettingsBackupRestore,
                    supporting = R.string.settings_import_data_description,
                    onClick = { onEvent(SettingsUiEvent.ImportData) },
                )
            }

            section(title = R.string.settings_about) {
                action(
                    title = R.string.settings_3rd_party_licenses,
                    icon = Icons.Default.Code,
                    onClick = { onEvent(SettingsUiEvent.LibrariesClicked) },
                )

                action(
                    title = R.string.settings_report_issue,
                    icon = Icons.Default.BugReport,
                    navigationIcon = Icons.AutoMirrored.Default.OpenInNew,
                    onClick = { onEvent(SettingsUiEvent.ReportIssue) }
                )

                value(
                    title = R.string.settings_version,
                    value = state.version,
                    icon = Icons.Default.Update,
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

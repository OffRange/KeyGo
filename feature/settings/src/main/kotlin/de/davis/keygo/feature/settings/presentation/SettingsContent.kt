package de.davis.keygo.feature.settings.presentation

import android.text.format.DateUtils
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
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.util.presentation.UIText
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
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
                    supporting = ResourceString(R.string.settings_reset_password_description),
                    onClick = { onEvent(SettingsUiEvent.ResetPassword) },
                )

                if (state.biometricsAvailable) toggle(
                    title = R.string.settings_use_biometrics,
                    icon = Icons.Default.Fingerprint,
                    supporting = ResourceString(R.string.settings_use_biometrics_description),
                    checked = state.biometricsEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetBiometrics(it)) },
                )

                toggle(
                    title = R.string.settings_autofill,
                    icon = Icons.Default.Password,
                    supporting = ResourceString(R.string.settings_autofill_description),
                    checked = state.autofillEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetAutofill(it)) },
                )
            }

            section(title = R.string.settings_backup) {
                action(
                    title = R.string.settings_backup_and_restore,
                    icon = Icons.Default.Backup,
                    supporting = lastBackupText(state.lastBackupAt),
                    onClick = { onEvent(SettingsUiEvent.OpenBackup) },
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

/**
 * Relative rather than absolute ("2 days ago", not a date): what matters at a glance is how stale
 * the newest backup is, so the row doubles as a nudge once it starts reading in weeks.
 */
private fun lastBackupText(lastBackupAt: Long?): UIText = when (lastBackupAt) {
    null -> ResourceString(R.string.settings_backup_none)
    else -> ResourceString(
        R.string.settings_backup_last,
        DateUtils.getRelativeTimeSpanString(
            lastBackupAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString(),
    )
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

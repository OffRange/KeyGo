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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
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
import de.davis.keygo.feature.settings.presentation.component.SectionScope
import de.davis.keygo.feature.settings.presentation.component.SettingsList

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    val defaultColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        leadingContentColor = MaterialTheme.colorScheme.primaryContainer,
    )
    val warningColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        supportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        leadingContentColor = MaterialTheme.colorScheme.primary,
        trailingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

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
                    colors = defaultColors,
                    onClick = { onEvent(SettingsUiEvent.ResetPassword) },
                )

                if (state.biometricsAvailable) toggle(
                    title = R.string.settings_use_biometrics,
                    icon = Icons.Default.Fingerprint,
                    supporting = ResourceString(R.string.settings_use_biometrics_description),
                    colors = defaultColors,
                    checked = state.biometricsEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetBiometrics(it)) },
                )

                toggle(
                    title = R.string.settings_autofill,
                    icon = Icons.Default.Password,
                    supporting = ResourceString(R.string.settings_autofill_description),
                    colors = defaultColors,
                    checked = state.autofillEnabled,
                    onCheckedChange = { onEvent(SettingsUiEvent.SetAutofill(it)) },
                )

                autofillEntries(state, warningColors, onEvent)
            }

            section(title = R.string.settings_backup) {
                action(
                    title = R.string.settings_backup_and_restore,
                    icon = Icons.Default.Backup,
                    supporting = lastBackupText(state.lastBackupAt),
                    colors = defaultColors,
                    onClick = { onEvent(SettingsUiEvent.OpenBackup) },
                )
            }

            section(title = R.string.settings_about) {
                action(
                    title = R.string.settings_3rd_party_licenses,
                    icon = Icons.Default.Code,
                    colors = defaultColors,
                    onClick = { onEvent(SettingsUiEvent.LibrariesClicked) },
                )

                action(
                    title = R.string.settings_report_issue,
                    icon = Icons.Default.BugReport,
                    navigationIcon = Icons.AutoMirrored.Default.OpenInNew,
                    colors = defaultColors,
                    onClick = { onEvent(SettingsUiEvent.ReportIssue) }
                )

                value(
                    title = R.string.settings_version,
                    value = state.version,
                    icon = Icons.Default.Update,
                    colors = defaultColors,
                )
            }
        }
    }
}

internal fun SectionScope.autofillEntries(
    state: SettingsUiState,
    warningColors: ListItemColors,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    if (state.autofillEnabled && !state.chromeAutofillEnabled) {
        action(
            title = R.string.settings_autofill_finish_setup,
            icon = Icons.Default.Public,
            supporting = ResourceString(R.string.settings_autofill_finish_setup_needs_chrome),
            colors = warningColors,
            navigationIcon = Icons.AutoMirrored.Default.OpenInNew,
            onClick = { onEvent(SettingsUiEvent.OpenChromeAutofillSettings) },
        )
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
                state = SettingsUiState(autofillEnabled = true),
                onEvent = {},
            )
        }
    }
}

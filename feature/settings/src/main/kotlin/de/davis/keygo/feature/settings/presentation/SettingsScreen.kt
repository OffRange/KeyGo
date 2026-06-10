package de.davis.keygo.feature.settings.presentation

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.identity.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.identity.presentation.rememberBiometricEnrollmentAdapter
import de.davis.keygo.core.identity.presentation.useEnrollmentAdapter
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.core.util.presentation.snackbar.LocalSnackbarManager
import de.davis.keygo.feature.settings.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    showLibraries: () -> Unit,
    onExportDataClicked: () -> Unit,
    onImportDataClicked: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val biometricController = rememberBiometricCryptoController()
    val enrollmentAdapter = rememberBiometricEnrollmentAdapter()

    val enableAutofillLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    // OS-owned state (autofill / biometric availability) can change while the user is
    // in a system screen; re-read it whenever we come back to the foreground.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSystemState()
        onPauseOrDispose {}
    }

    val urlHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarManager = LocalSnackbarManager.current
    ObserveAsEvents(viewModel.event) {
        when (it) {
            SettingsEvent.NavigateToLibraries -> showLibraries()

            SettingsEvent.NavigateToChangePassword -> onOpenChangePassword()

            SettingsEvent.OpenAutofillSelection -> {
                enableAutofillLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                )
            }

            is SettingsEvent.EnableBiometric -> {
                val result = when {
                    it.enable -> enrollmentAdapter.useEnrollmentAdapter {
                        biometricController.requestEnableBiometric()
                    }

                    else -> enrollmentAdapter.disableBiometric()
                }

                result.onFailure { error ->
                    if (!error.isUserDismissal()) snackbarManager.sendMessage(
                        SnackbarMessage(
                            message = ResourceString(R.string.settings_biometric_update_failed),
                        ),
                    )
                }
            }

            SettingsEvent.ReportIssue -> urlHandler.openUri(ISSUES_URL)

            SettingsEvent.ExportData -> onExportDataClicked()
            SettingsEvent.ImportData -> onImportDataClicked()
        }
    }

    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

/** The user backing out of the prompt is not an error worth a snackbar. */
private fun BiometricEnrollmentError.isUserDismissal(): Boolean =
    this is BiometricEnrollmentError.BiometricFailed &&
            (error == BiometricAuthError.Declined || error == BiometricAuthError.Canceled)

private const val ISSUES_URL = "https://github.com/OffRange/KeyGo/issues/new"
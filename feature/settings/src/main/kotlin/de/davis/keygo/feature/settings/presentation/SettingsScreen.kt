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
import de.davis.keygo.core.identity.presentation.rememberBiometricEnrollmentAdapter
import de.davis.keygo.core.identity.presentation.useEnrollmentAdapter
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(showLibraries: () -> Unit) {
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
    ObserveAsEvents(viewModel.event) {
        when (it) {
            SettingsEvent.NavigateToLibraries -> showLibraries()

            SettingsEvent.OpenAutofillSelection -> {
                enableAutofillLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                )
            }

            is SettingsEvent.EnableBiometric -> {
                when {
                    it.enable -> {
                        enrollmentAdapter.useEnrollmentAdapter {
                            biometricController.requestEnableBiometric()
                        }
                    }

                    else -> enrollmentAdapter.disableBiometric()
                }
            }

            SettingsEvent.ReportIssue -> urlHandler.openUri(ISSUES_URL)
        }
    }

    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

private const val ISSUES_URL = "https://github.com/OffRange/KeyGo/issues/new"
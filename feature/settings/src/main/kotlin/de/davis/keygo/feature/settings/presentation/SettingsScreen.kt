package de.davis.keygo.feature.settings.presentation

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.security.presentation.rememberHandoffLauncher
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

private const val TAG = "SettingsScreen"

@Composable
fun SettingsScreen(
    showLibraries: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenChangePassword: () -> Unit,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val enableAutofillLauncher =
        rememberHandoffLauncher(ActivityResultContracts.StartActivityForResult()) {}

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

            SettingsEvent.NavigateToChangePassword -> onOpenChangePassword()

            SettingsEvent.OpenAutofillSelection -> {
                enableAutofillLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                ).onFailure {
                    Log.w(TAG, "No activity found to handle the autofill selection", it)
                }
            }

            SettingsEvent.ReportIssue -> urlHandler.openUri(ISSUES_URL)

            SettingsEvent.NavigateToBackup -> onOpenBackup()
        }
    }

    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

private const val ISSUES_URL = "https://github.com/OffRange/KeyGo/issues/new"

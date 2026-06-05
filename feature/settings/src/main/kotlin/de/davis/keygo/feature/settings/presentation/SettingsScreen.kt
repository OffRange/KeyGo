package de.davis.keygo.feature.settings.presentation

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen() {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val enableAutofillLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.reset()
        }

    val context = LocalContext.current
    ObserveAsEvents(viewModel.event) {
        when (it) {
            is SettingsEvent.OpenAutofillSelection -> {
                enableAutofillLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                )
            }
        }
    }

    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}
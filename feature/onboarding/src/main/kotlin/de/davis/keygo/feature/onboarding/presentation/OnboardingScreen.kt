package de.davis.keygo.feature.onboarding.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.backup.presentation.import.ImportWizardScreen
import de.davis.keygo.feature.backup.presentation.import.rememberImportFilePicker
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.model.AutofillSetupAction
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingUiState
import org.koin.androidx.compose.koinViewModel

private const val TAG = "OnboardingScreen"
private val OnboardingMaxWidth = 480.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onSuccess: () -> Unit) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val biometricCryptoController = rememberBiometricCryptoController()
    ObserveAsEvents(viewModel.biometricFlow) {
        biometricCryptoController.requestCipher(
            keyId = KeyId.BiometricVaultKek,
            mode = CryptographicMode.Wrap
        ).onSuccess {
            viewModel.performCreateAccess(it)
        }.onFailure {
            Log.e("OnboardingScreen", "Failed to create cipher for biometric access: $it")
            // TODO: show error
        }
    }

    ObserveAsEvents(viewModel.finishedFlow) {
        onSuccess()
    }

    val context = LocalContext.current
    val autofillPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    ObserveAsEvents(viewModel.autofillPickerFlow) {
        try {
            autofillPickerLauncher.launch(
                Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = "package:${context.packageName}".toUri()
                }
            )
        } catch (e: ActivityNotFoundException) {
            // Some AOSP builds, Android TV, and a few OEM ROMs have nothing that resolves this
            // intent. The user still has the "Finish setup" button to move past the step, so
            // failing quietly here is acceptable as long as it stays diagnosable.
            Log.w(TAG, "No activity found to handle the system autofill picker", e)
        }
    }

    // The picker reports nothing back and Chrome's hand off is not a result flow at all, so the
    // resume read is what actually learns the new state. Keying on the step also refreshes on
    // arrival, and keeps Chrome's cross process query off every other step's resume.
    val onAutofillStep = state is OnboardingUiState.EnableAutofill
    LifecycleResumeEffect(onAutofillStep) {
        if (onAutofillStep) viewModel.refreshAutofillState()
        onPauseOrDispose {}
    }

    val chooseImportFile = rememberImportFilePicker(viewModel::onImportFileChosen)

    // The wizard brings its own Scaffold, top bar, step indicator and continue button, so it
    // replaces the step chrome rather than rendering inside it. It also owns back for every one of
    // its phases while it holds a preselected file, so onboarding adds no handler of its own.
    val importFile = (state as? OnboardingUiState.ImportData)?.fileUri
    if (importFile != null) ImportWizardScreen(
        preselectedFile = importFile,
        navigateUp = viewModel::onImportCancelled,
        onFinished = viewModel::onImportFinished,
    )
    else OnboardingSteps(
        state = state,
        loading = loading,
        onNextStep = viewModel::onNextStep,
        onSkip = viewModel::onSkip,
        onChooseImportFile = chooseImportFile::launch,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingSteps(
    state: OnboardingUiState,
    loading: Boolean,
    onNextStep: () -> Unit,
    onSkip: () -> Unit,
    onChooseImportFile: () -> Unit,
) {
    val contentHeight = ButtonDefaults.LargeContainerHeight
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .widthIn(max = OnboardingMaxWidth),
                ) {
                    state.optionalActionText?.let {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = it)
                        }
                    }

                    if (state.isOutlinedButonCandidate())
                        OutlinedButton(
                            onClick = onNextStep,
                            shapes = ButtonDefaults.shapesFor(contentHeight),
                            modifier = Modifier
                                .sizeIn(minHeight = contentHeight)
                                .fillMaxWidth(),
                            contentPadding = ButtonDefaults.contentPaddingFor(contentHeight)
                        ) {
                            Text(
                                text = state.buttonText,
                                style = ButtonDefaults.textStyleFor(contentHeight),
                            )
                        }
                    else Button(
                        onClick = onNextStep,
                        shapes = ButtonDefaults.shapesFor(contentHeight),
                        modifier = Modifier
                            .sizeIn(minHeight = contentHeight)
                            .fillMaxWidth(),
                        contentPadding = ButtonDefaults.contentPaddingFor(contentHeight)
                    ) {
                        Text(
                            text = state.buttonText,
                            style = ButtonDefaults.textStyleFor(contentHeight),
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                modifier = Modifier.widthIn(max = OnboardingMaxWidth),
            ) { state ->
                when (state) {
                    is OnboardingUiState.Welcome -> WelcomeContent(
                        state = state,
                        onContinue = onNextStep
                    )

                    is OnboardingUiState.SetMainPassword -> MainPasswordContent(state = state)

                    OnboardingUiState.EnableBiometrics -> EnableBiometricsContent()
                    is OnboardingUiState.ImportData -> ImportVaultContent(
                        onChooseFile = onChooseImportFile,
                    )

                    is OnboardingUiState.EnableAutofill -> EnableAutofillContent(state = state)
                }
            }
        }
    }

    if (loading)
        BasicAlertDialog(
            onDismissRequest = {}
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        }
}

private val OnboardingUiState.buttonText: String
    @Composable
    get() = stringResource(
        when (this) {
            is OnboardingUiState.Welcome -> if (migrating) R.string.migrate else R.string.get_started
            is OnboardingUiState.SetMainPassword -> R.string.continue_text
            OnboardingUiState.EnableBiometrics -> R.string.enable_biometrics
            is OnboardingUiState.ImportData -> R.string.skip_for_now
            is OnboardingUiState.EnableAutofill -> when (nextAction) {
                AutofillSetupAction.OpenSystemSettings -> R.string.autofill_open_settings
                AutofillSetupAction.OpenChromeSettings -> R.string.autofill_enable_in_chrome
                AutofillSetupAction.Finish -> R.string.finish_setup
            }
        }
    )


private val OnboardingUiState.optionalActionText: String?
    @Composable
    get() = when (this) {
        is OnboardingUiState.Welcome,
        is OnboardingUiState.ImportData,
        is OnboardingUiState.SetMainPassword -> null

        OnboardingUiState.EnableBiometrics -> R.string.skip_for_now
        is OnboardingUiState.EnableAutofill ->
            R.string.finish_setup.takeIf { nextAction != AutofillSetupAction.Finish }
    }?.let { stringResource(it) }

private fun OnboardingUiState.isOutlinedButonCandidate() = this is OnboardingUiState.ImportData

@Preview
@PreviewScreenSizes
@Composable
private fun OnboardingStepsPreview() {
    MaterialTheme {
        OnboardingSteps(
            state = OnboardingUiState.Welcome(),
            loading = false,
            onNextStep = {},
            onSkip = {},
            onChooseImportFile = {},
        )
    }
}

package de.davis.keygo.feature.onboarding.presentation

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingUiState
import org.koin.androidx.compose.koinViewModel

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

    val contentHeight = ButtonDefaults.LargeContainerHeight
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        bottomBar = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                state.optionalActionText?.let {
                    TextButton(
                        onClick = viewModel::onSkip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = it)
                    }
                }

                if (state.isOutlinedButonCandidate())
                    OutlinedButton(
                        onClick = viewModel::onNextStep,
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
                    onClick = viewModel::onNextStep,
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
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            AnimatedContent(state, contentKey = { it::class }) { state ->
                when (state) {
                    OnboardingUiState.Welcome -> WelcomeContent(
                        onContinue = viewModel::onNextStep
                    )

                    is OnboardingUiState.SetMainPassword -> MainPasswordContent(state = state)

                    OnboardingUiState.EnableBiometrics -> EnableBiometricsContent()
                    OnboardingUiState.ImportData -> ImportVaultContent()
                    OnboardingUiState.EnableAutofill -> EnableAutofillContent()
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
            OnboardingUiState.Welcome -> R.string.get_started
            is OnboardingUiState.SetMainPassword -> R.string.continue_text
            OnboardingUiState.EnableBiometrics -> R.string.enable_biometrics
            OnboardingUiState.ImportData -> R.string.skip_for_now
            OnboardingUiState.EnableAutofill -> R.string.open_settings
        }
    )


private val OnboardingUiState.optionalActionText: String?
    @Composable
    get() = when (this) {
        OnboardingUiState.Welcome,
        OnboardingUiState.ImportData,
        is OnboardingUiState.SetMainPassword -> null

        OnboardingUiState.EnableBiometrics -> R.string.skip_for_now
        OnboardingUiState.EnableAutofill -> R.string.finish_setup
    }?.let { stringResource(it) }

private fun OnboardingUiState.isOutlinedButonCandidate() = this is OnboardingUiState.ImportData

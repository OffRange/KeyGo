package de.davis.keygo.feature.onboarding.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.feature.autofill.domain.model.AutofillActivationStatus
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri

internal sealed interface OnboardingUiState {

    data class Welcome(val pendingTotpImport: Boolean = false) : OnboardingUiState

    data class SetMainPassword(
        val passwordTextFieldState: TextFieldState,
        val confirmPasswordTextFieldState: TextFieldState,
        val passwordScore: PasswordScore,
        val passwordError: UiFieldError? = null,
        val confirmPasswordError: UiFieldError? = null,
    ) : OnboardingUiState

    data object EnableBiometrics : OnboardingUiState

    /**
     * @param fileUri the file the user picked to import. While it is set the import wizard owns the
     * screen, and clearing it returns to the chooser.
     */
    data class ImportData(val fileUri: BackupDestinationUri? = null) : OnboardingUiState

    data class EnableAutofill(
        val activationStatus: AutofillActivationStatus = AutofillActivationStatus(),
    ) : OnboardingUiState {

        val nextAction: AutofillSetupAction
            get() = when {
                !activationStatus.systemAutofillEnabled -> AutofillSetupAction.OpenSystemSettings
                activationStatus.chromeAvailable && !activationStatus.chromeAutofillEnabled -> AutofillSetupAction.OpenChromeSettings
                else -> AutofillSetupAction.Finish
            }
    }
}

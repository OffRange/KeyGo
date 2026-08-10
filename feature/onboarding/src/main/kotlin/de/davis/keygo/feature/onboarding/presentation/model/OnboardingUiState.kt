package de.davis.keygo.feature.onboarding.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.ui.model.UiFieldError
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
        val systemAutofillEnabled: Boolean = false,
        val chromeAvailable: Boolean = false,
        val chromeAutofillEnabled: Boolean = false,
    ) : OnboardingUiState {

        /**
         * Single source of truth for the primary button: the ViewModel reads it to decide what to
         * do, the screen reads it to decide what to say. Driven by state rather than a counter, so
         * a device that already has Chrome on but KeyGo unselected still starts at the picker and
         * then goes straight to done.
         */
        val nextAction: AutofillSetupAction
            get() = when {
                !systemAutofillEnabled -> AutofillSetupAction.OpenSystemSettings
                chromeAvailable && !chromeAutofillEnabled -> AutofillSetupAction.OpenChromeSettings
                else -> AutofillSetupAction.Finish
            }
    }
}

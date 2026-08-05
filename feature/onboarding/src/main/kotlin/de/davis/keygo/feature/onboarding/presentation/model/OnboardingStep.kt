package de.davis.keygo.feature.onboarding.presentation.model

internal enum class OnboardingStep {
    Welcome,
    SetMainPassword,
    EnableBiometrics,
    ImportExistingData,
    EnableAutofillService;

    fun nextStep(): OnboardingStep? {
        return when (this) {
            Welcome -> SetMainPassword
            SetMainPassword -> EnableBiometrics
            EnableBiometrics -> ImportExistingData
            ImportExistingData -> EnableAutofillService
            EnableAutofillService -> null
        }
    }
}
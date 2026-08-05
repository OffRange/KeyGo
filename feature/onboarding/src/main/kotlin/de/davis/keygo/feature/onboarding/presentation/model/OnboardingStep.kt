package de.davis.keygo.feature.onboarding.presentation.model

internal enum class OnboardingStep {
    Welcome,
    SetMainPassword,
    EnableBiometrics,
    ImportExistingData,
    EnableAutofillService;

    fun nextStep(skip: Set<OnboardingStep>): OnboardingStep? {
        val pool = OnboardingStep.entries.filterNot { it in skip }
        return pool.getOrNull(pool.indexOf(this) + 1)
    }
}
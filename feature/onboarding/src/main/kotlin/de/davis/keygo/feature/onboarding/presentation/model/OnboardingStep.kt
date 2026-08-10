package de.davis.keygo.feature.onboarding.presentation.model

internal enum class OnboardingStep {
    Welcome,
    SetMainPassword,
    EnableBiometrics,
    ImportExistingData,
    EnableAutofillService;

    fun nextStep(skip: Set<OnboardingStep>): OnboardingStep? {
        val pool = OnboardingStep.entries.filterNot { it in skip }
        val currentIndex = pool.indexOf(this)
        return if (currentIndex != -1) pool.getOrNull(currentIndex + 1) else null
    }
}
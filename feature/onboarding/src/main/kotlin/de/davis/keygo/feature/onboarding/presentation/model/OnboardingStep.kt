package de.davis.keygo.feature.onboarding.presentation.model

internal enum class OnboardingStep {
    Welcome,
    SetMainPassword,
    EnableBiometrics,
    ImportExistingData,
    EnableAutofillService;

    fun nextStep(skip: Set<OnboardingStep>): OnboardingStep? {
        val pool = activeSteps(skip)
        val currentIndex = pool.indexOf(this)
        return if (currentIndex != -1) pool.getOrNull(currentIndex + 1) else null
    }

    fun previousStep(skip: Set<OnboardingStep>): OnboardingStep? {
        if (!canGoBack) return null
        val pool = activeSteps(skip)
        val currentIndex = pool.indexOf(this)
        return if (currentIndex != -1) pool.getOrNull(currentIndex - 1) else null
    }

    val canGoBack: Boolean
        get() = this == SetMainPassword || this == EnableBiometrics || this == EnableAutofillService

    companion object {
        fun activeSteps(skip: Set<OnboardingStep>): List<OnboardingStep> =
            entries.filterNot { it in skip }
    }
}
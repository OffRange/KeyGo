package de.davis.keygo.feature.onboarding.presentation.model

internal data class OnboardingStepProgress(
    val currentIndex: Int,
    val totalSteps: Int,
    val canGoBack: Boolean,
)

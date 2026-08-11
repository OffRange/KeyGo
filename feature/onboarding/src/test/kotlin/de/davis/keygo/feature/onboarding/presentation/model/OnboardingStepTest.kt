package de.davis.keygo.feature.onboarding.presentation.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnboardingStepTest {

    @Test
    fun `finishing the import moves on to autofill setup`() {
        assertEquals(
            OnboardingStep.EnableAutofillService,
            OnboardingStep.ImportExistingData.nextStep(skip = emptySet()),
        )
    }

    @Test
    fun `finishing the import ends onboarding when autofill is already set up`() {
        assertNull(
            OnboardingStep.ImportExistingData.nextStep(
                skip = setOf(OnboardingStep.EnableAutofillService),
            ),
        )
    }

    @Test
    fun `a migrating user never reaches the import step`() {
        assertEquals(
            OnboardingStep.EnableAutofillService,
            OnboardingStep.EnableBiometrics.nextStep(
                skip = setOf(OnboardingStep.ImportExistingData),
            ),
        )
    }

    @Test
    fun `every step but welcome and import allows going back`() {
        assertEquals(
            setOf(
                OnboardingStep.SetMainPassword,
                OnboardingStep.EnableBiometrics,
                OnboardingStep.EnableAutofillService,
            ),
            OnboardingStep.entries.filter { it.canGoBack }.toSet(),
        )
    }

    @Test
    fun `stepping back from biometrics returns to the password step`() {
        assertEquals(
            OnboardingStep.SetMainPassword,
            OnboardingStep.EnableBiometrics.previousStep(skip = emptySet()),
        )
    }

    @Test
    fun `stepping back from the password step returns to welcome`() {
        assertEquals(
            OnboardingStep.Welcome,
            OnboardingStep.SetMainPassword.previousStep(skip = emptySet()),
        )
    }

    @Test
    fun `welcome has nothing to go back to`() {
        assertNull(OnboardingStep.Welcome.previousStep(skip = emptySet()))
    }

    @Test
    fun `import is a dead end going backwards`() {
        assertNull(OnboardingStep.ImportExistingData.previousStep(skip = emptySet()))
    }

    @Test
    fun `stepping back from autofill returns to the import step`() {
        assertEquals(
            OnboardingStep.ImportExistingData,
            OnboardingStep.EnableAutofillService.previousStep(skip = emptySet()),
        )
    }

    @Test
    fun `stepping back from autofill still lands on import when biometrics was skipped`() {
        assertEquals(
            OnboardingStep.ImportExistingData,
            OnboardingStep.EnableAutofillService.previousStep(
                skip = setOf(OnboardingStep.EnableBiometrics),
            ),
        )
    }
}

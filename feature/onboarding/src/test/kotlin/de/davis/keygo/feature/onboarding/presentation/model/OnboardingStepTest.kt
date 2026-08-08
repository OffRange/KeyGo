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
}

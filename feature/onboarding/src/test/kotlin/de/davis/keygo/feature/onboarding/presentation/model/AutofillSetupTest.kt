package de.davis.keygo.feature.onboarding.presentation.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AutofillSetupTest {

    @Test
    fun `next action opens system settings while KeyGo is not the autofill service`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = false,
            chromeAvailable = true,
            chromeAutofillEnabled = false,
        )

        assertEquals(AutofillSetupAction.OpenSystemSettings, state.nextAction)
    }

    @Test
    fun `next action opens system settings even when chrome is already on`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = false,
            chromeAvailable = true,
            chromeAutofillEnabled = true,
        )

        assertEquals(AutofillSetupAction.OpenSystemSettings, state.nextAction)
    }

    @Test
    fun `next action opens chrome settings once the system service is set`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = true,
            chromeAvailable = true,
            chromeAutofillEnabled = false,
        )

        assertEquals(AutofillSetupAction.OpenChromeSettings, state.nextAction)
    }

    @Test
    fun `next action finishes when both are enabled`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = true,
            chromeAvailable = true,
            chromeAutofillEnabled = true,
        )

        assertEquals(AutofillSetupAction.Finish, state.nextAction)
    }

    @Test
    fun `next action finishes when the system service is set and chrome is unavailable`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = true,
            chromeAvailable = false,
            chromeAutofillEnabled = false,
        )

        assertEquals(AutofillSetupAction.Finish, state.nextAction)
    }

    @Test
    fun `setup steps start with the first row current and the rest upcoming`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = false,
            chromeAvailable = true,
            chromeAutofillEnabled = false,
        )

        assertEquals(
            listOf(
                AutofillSetupStep.OpenSystemSettings to AutofillSetupStatus.Current,
                AutofillSetupStep.ChooseKeyGo to AutofillSetupStatus.Upcoming,
                AutofillSetupStep.EnableInChrome to AutofillSetupStatus.Upcoming,
            ),
            state.setupSteps(),
        )
    }

    @Test
    fun `setup steps mark both system rows done together and chrome current`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = true,
            chromeAvailable = true,
            chromeAutofillEnabled = false,
        )

        assertEquals(
            listOf(
                AutofillSetupStep.OpenSystemSettings to AutofillSetupStatus.Done,
                AutofillSetupStep.ChooseKeyGo to AutofillSetupStatus.Done,
                AutofillSetupStep.EnableInChrome to AutofillSetupStatus.Current,
            ),
            state.setupSteps(),
        )
    }

    @Test
    fun `setup steps omit the chrome row when chrome is unavailable`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = false,
            chromeAvailable = false,
            chromeAutofillEnabled = false,
        )

        assertEquals(
            listOf(
                AutofillSetupStep.OpenSystemSettings to AutofillSetupStatus.Current,
                AutofillSetupStep.ChooseKeyGo to AutofillSetupStatus.Upcoming,
            ),
            state.setupSteps(),
        )
    }

    @Test
    fun `setup steps mark every row done once both are enabled`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = true,
            chromeAvailable = true,
            chromeAutofillEnabled = true,
        )

        assertEquals(
            listOf(
                AutofillSetupStep.OpenSystemSettings to AutofillSetupStatus.Done,
                AutofillSetupStep.ChooseKeyGo to AutofillSetupStatus.Done,
                AutofillSetupStep.EnableInChrome to AutofillSetupStatus.Done,
            ),
            state.setupSteps(),
        )
    }

    @Test
    fun `setup steps show chrome already done while the system rows are still pending`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = false,
            chromeAvailable = true,
            chromeAutofillEnabled = true,
        )

        assertEquals(
            listOf(
                AutofillSetupStep.OpenSystemSettings to AutofillSetupStatus.Current,
                AutofillSetupStep.ChooseKeyGo to AutofillSetupStatus.Upcoming,
                AutofillSetupStep.EnableInChrome to AutofillSetupStatus.Done,
            ),
            state.setupSteps(),
        )
    }

    @Test
    fun `setup steps have no current row when only the chrome row is missing`() {
        val state = OnboardingUiState.EnableAutofill(
            systemAutofillEnabled = true,
            chromeAvailable = false,
            chromeAutofillEnabled = false,
        )

        assertEquals(
            listOf(
                AutofillSetupStep.OpenSystemSettings to AutofillSetupStatus.Done,
                AutofillSetupStep.ChooseKeyGo to AutofillSetupStatus.Done,
            ),
            state.setupSteps(),
        )
    }
}

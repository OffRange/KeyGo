package de.davis.keygo.feature.onboarding.presentation.model

/** What the primary button on the autofill step does next. */
internal enum class AutofillSetupAction {
    OpenSystemSettings,
    OpenChromeSettings,
    Finish,
}

/** One row of the instruction list on the autofill step. */
internal enum class AutofillSetupStep {
    OpenSystemSettings,
    ChooseKeyGo,
    EnableInChrome,
}

internal enum class AutofillSetupStatus {
    Done,
    Current,
    Upcoming,
}

/**
 * The rows to render, in order. Opening the picker and picking KeyGo are a single act for the user,
 * so both follow the system service flag and flip to done together. The Chrome row is dropped
 * entirely when there is no Chrome to act on.
 */
internal fun OnboardingUiState.EnableAutofill.setupSteps(): List<Pair<AutofillSetupStep, AutofillSetupStatus>> {
    val steps = listOfNotNull(
        AutofillSetupStep.OpenSystemSettings to systemAutofillEnabled,
        AutofillSetupStep.ChooseKeyGo to systemAutofillEnabled,
        (AutofillSetupStep.EnableInChrome to chromeAutofillEnabled).takeIf { chromeAvailable },
    )

    val currentIndex = steps.indexOfFirst { (_, done) -> !done }
    return steps.mapIndexed { index, (step, done) ->
        step to when {
            done -> AutofillSetupStatus.Done
            index == currentIndex -> AutofillSetupStatus.Current
            else -> AutofillSetupStatus.Upcoming
        }
    }
}

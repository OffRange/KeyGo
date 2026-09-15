package de.davis.keygo.feature.autofill.domain.model

data class AutofillActivationStatus(
    val systemAutofillEnabled: Boolean = false,
    val chromeAvailable: Boolean = false,
    val chromeAutofillEnabled: Boolean = false,
)

package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.model.ChromeAutofillState
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository

class FakeChromeAutofillRepository : ChromeAutofillRepository {

    // Chrome is present and exposes third party autofill mode. Flip it to model a device with no
    // Chrome, where the state reads as unavailable whatever enabled says.
    var available: Boolean = true

    var enabled: Boolean = false

    var openCalled: Boolean = false

    override suspend fun autofillState(): ChromeAutofillState = when {
        !available -> ChromeAutofillState.Unavailable
        enabled -> ChromeAutofillState.Enabled
        else -> ChromeAutofillState.Disabled
    }

    override fun openChromeAutofillSettings() {
        openCalled = true
    }
}

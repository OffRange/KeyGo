package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository

class FakeChromeAutofillRepository : ChromeAutofillRepository {

    // Chrome is present and exposes third party autofill mode. Flip it to model a device with no
    // Chrome, where the enabled read can never come back true.
    var available: Boolean = true

    var enabled: Boolean = false

    var openCalled: Boolean = false

    override suspend fun isAvailable(): Boolean = available

    override suspend fun isAutofillEnabled(): Boolean = available && enabled

    override fun openChromeAutofillSettings() {
        openCalled = true
    }
}

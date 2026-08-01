package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository

class FakeChromeAutofillRepository : ChromeAutofillRepository {

    var enabled: Boolean = false

    var openCalled: Boolean = false

    override fun isAutofillEnabled(): Boolean = enabled

    override fun openChromeAutofillSettings() {
        openCalled = true
    }
}

package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository

class FakeAutofillServiceRepository : AutofillServiceRepository {

    var enabled: Boolean = false
    var disableCalled: Boolean = false

    override fun isEnabled(): Boolean = enabled

    override fun disable() {
        disableCalled = true
        enabled = false
    }
}

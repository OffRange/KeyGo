package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository

class FakeAutofillServiceRepository : AutofillServiceRepository {

    // The authoritative OS state. Tests set it directly to mirror the user selecting/clearing
    // KeyGo as the autofill service (e.g. from the system picker) between refreshSystemState reads.
    var enabled: Boolean = false

    var disableCalled: Boolean = false

    override fun isEnabled(): Boolean = enabled

    override fun disable() {
        disableCalled = true
        enabled = false
    }
}

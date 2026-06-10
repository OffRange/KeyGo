package de.davis.keygo.core.feature.autofill

import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository

class FakeAutofillServiceRepository : AutofillServiceRepository {

    // Mirrors the OS: disableAutofillServices() propagates asynchronously, so
    // isEnabled() can keep reporting true right after disable(). Tests control
    // `enabled` explicitly instead of disable() flipping it synchronously.
    var enabled: Boolean = false
    var disableCalled: Boolean = false

    override fun isEnabled(): Boolean = enabled

    override fun disable() {
        disableCalled = true
    }
}

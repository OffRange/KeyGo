package de.davis.keygo.feature.autofill.domain.repository

import de.davis.keygo.feature.autofill.domain.model.ChromeAutofillState

interface ChromeAutofillRepository {

    suspend fun autofillState(): ChromeAutofillState

    fun openChromeAutofillSettings()
}

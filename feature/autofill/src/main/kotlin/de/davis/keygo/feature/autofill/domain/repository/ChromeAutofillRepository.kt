package de.davis.keygo.feature.autofill.domain.repository

interface ChromeAutofillRepository {

    fun isAutofillEnabled(): Boolean

    fun openChromeAutofillSettings()
}
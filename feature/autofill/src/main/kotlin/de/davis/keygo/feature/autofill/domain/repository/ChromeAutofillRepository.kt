package de.davis.keygo.feature.autofill.domain.repository

interface ChromeAutofillRepository {

    /**
     * Whether Chrome is installed and exposes third party autofill mode. Callers that offer to open
     * Chrome's settings should check this first: without the provider there is nothing to read and
     * nothing for the user to turn on.
     */
    suspend fun isAvailable(): Boolean

    suspend fun isAutofillEnabled(): Boolean

    fun openChromeAutofillSettings()
}

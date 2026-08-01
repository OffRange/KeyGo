package de.davis.keygo.feature.autofill.domain.repository

interface AutofillServiceRepository {

    /**
     * Authoritative, immediate read of whether KeyGo is the currently selected autofill service.
     * Read on lifecycle resume to re-sync after the user returns from the system autofill picker
     * (or any other screen), which is where the selection actually changes.
     */
    fun isEnabled(): Boolean

    fun disable()
}

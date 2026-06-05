package de.davis.keygo.feature.autofill.domain.repository

interface AutofillServiceRepository {

    fun isEnabled(): Boolean
    fun disable()
}
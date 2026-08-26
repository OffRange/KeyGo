package de.davis.keygo.feature.autofill.domain.repository

import de.davis.keygo.feature.autofill.domain.model.SmsCodeEvent
import kotlinx.coroutines.flow.Flow

interface SmsCodeRepository {

    suspend fun canOfferSuggestion(targetPackage: String): Boolean
    fun smsCodes(): Flow<SmsCodeEvent>
}
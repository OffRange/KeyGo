package de.davis.keygo.feature.autofill.domain.repository

import de.davis.keygo.feature.autofill.domain.model.SmsCodeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
internal class NoopSmsCodeRepository : SmsCodeRepository {

    override suspend fun canOfferSuggestion(targetPackage: String): Boolean = false

    override fun smsCodes(): Flow<SmsCodeEvent> =
        flowOf(SmsCodeEvent.Failed(IllegalStateException("SMS code autofill unavailable")))
}
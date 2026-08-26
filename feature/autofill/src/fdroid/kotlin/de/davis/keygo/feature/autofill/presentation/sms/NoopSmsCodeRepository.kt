package de.davis.keygo.feature.autofill.presentation.sms

import de.davis.keygo.core.util.Result
import org.koin.core.annotation.Single

@Single(binds = [SmsCodeRepository::class])
internal class NoopSmsCodeRepository : SmsCodeRepository {

    override suspend fun canOfferSuggestion(targetPackage: String): Boolean = false

    override suspend fun retrieveSmsCode(): Result<String, SmsCodeFailure> =
        Result.Failure(SmsCodeFailure.Unavailable)
}

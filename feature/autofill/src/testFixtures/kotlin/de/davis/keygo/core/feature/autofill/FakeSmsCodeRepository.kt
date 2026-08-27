package de.davis.keygo.core.feature.autofill

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.presentation.sms.SmsCodeFailure
import de.davis.keygo.feature.autofill.presentation.sms.SmsCodeRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * Hands out scripted [retrieveSmsCode] results, one per call, so a consent round trip can be played
 * back. Setting [gate] makes each call suspend on it first, which lets a test hold a retrieval open
 * and check that cancelling it really takes effect.
 */
internal class FakeSmsCodeRepository : SmsCodeRepository {

    var canOffer: Boolean = true
    var gate: CompletableDeferred<Unit>? = null

    var callCount: Int = 0
        private set

    private val results = ArrayDeque<Result<String, SmsCodeFailure>>()

    fun enqueue(vararg values: Result<String, SmsCodeFailure>) {
        results += values
    }

    override suspend fun canOfferSuggestion(targetPackage: String): Boolean = canOffer

    override suspend fun retrieveSmsCode(): Result<String, SmsCodeFailure> {
        callCount++
        gate?.await()
        return results.removeFirstOrNull()
            ?: Result.Failure(SmsCodeFailure.Unknown(IllegalStateException("no result enqueued")))
    }
}

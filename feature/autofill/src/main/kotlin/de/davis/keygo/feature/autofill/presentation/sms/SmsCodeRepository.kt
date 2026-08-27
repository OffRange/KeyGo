package de.davis.keygo.feature.autofill.presentation.sms

import de.davis.keygo.core.util.Result

/**
 * Reads one time codes out of incoming SMS messages.
 *
 * This lives in presentation rather than domain on purpose. It carries no policy, only platform
 * mechanism, and the consent failure has to hand back a framework [android.content.IntentSender].
 */
internal interface SmsCodeRepository {

    suspend fun canOfferSuggestion(targetPackage: String): Boolean

    /**
     * Waits for a single SMS verification code. Cancelling the calling coroutine stops the wait and
     * releases the underlying receiver.
     */
    suspend fun retrieveSmsCode(): Result<String, SmsCodeFailure>
}

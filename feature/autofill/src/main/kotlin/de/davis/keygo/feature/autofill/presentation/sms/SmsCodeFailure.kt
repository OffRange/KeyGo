package de.davis.keygo.feature.autofill.presentation.sms

import android.content.IntentSender

internal sealed interface SmsCodeFailure {

    /**
     * The user has not allowed KeyGo to read SMS verification codes yet. Launching [intentSender]
     * lets Google Play services ask them, after which the retrieval can be tried again.
     */
    data class ConsentRequired(val intentSender: IntentSender) : SmsCodeFailure

    /** Google Play services waited its full window (about 5 minutes) without seeing a code. */
    data object Timeout : SmsCodeFailure

    /** SMS code retrieval is not available at all, for example on the fdroid flavor. */
    data object Unavailable : SmsCodeFailure

    data class Unknown(val cause: Throwable) : SmsCodeFailure
}

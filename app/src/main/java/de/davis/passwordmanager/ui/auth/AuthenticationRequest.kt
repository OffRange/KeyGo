package de.davis.passwordmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import de.davis.passwordmanager.R
import kotlinx.parcelize.Parcelize


const val EXTRA_AUTH_REQUEST: String = "de.davis.passwordmanager.extra.AUTH_REQUEST"

@IntDef(RequestType.AUTH_REQUEST_AUTHENTICATE, RequestType.AUTH_REQUEST_CHANGE_MAIN_PASSWORD)
annotation class RequestType {
    companion object {
        const val AUTH_REQUEST_AUTHENTICATE: Int = 0
        const val AUTH_REQUEST_CHANGE_MAIN_PASSWORD: Int = 1
    }
}

@Parcelize
class AuthenticationRequest internal constructor(
    @RequestType val requestType: Int = RequestType.AUTH_REQUEST_AUTHENTICATE,
    @StringRes val message: Int = if (requestType == RequestType.AUTH_REQUEST_AUTHENTICATE) R.string.authenticate else R.string.set_main_password_info,
    val additionalExtras: Bundle? = null,
    val intent: Intent? = null
) : Parcelable {

    open class Builder(@RequestType private val requestType: Int = RequestType.AUTH_REQUEST_AUTHENTICATE) {

        @StringRes
        var message: Int =
            if (requestType == RequestType.AUTH_REQUEST_AUTHENTICATE) R.string.authenticate else R.string.set_main_password_info

        private var additionalExtras: Bundle? = null
        private var intent: Intent? = null

        /**
         * Represents additional extras to be passed as data to authentication-required processes.
         * These extras can be accessed as part of the result data when using
         * [androidx.activity.result.ActivityResultCaller.registerForActivityResult] to handle
         * authentication-required activities.
         */
        fun withAdditionalExtras(additionalExtras: Bundle) = apply {
            this.additionalExtras = additionalExtras
        }

        /**
         * Sets an intent to be used after a successful authentication process.
         *
         * @param intent The intent to be started.
         */
        fun withIntent(intent: Intent) = apply {
            this.intent = intent
        }

        /**
         * Sets a custom message resource ID for displaying information to the user.
         *
         * **Note:** This string should be short and meaningful.
         *
         * @param message The message resource ID.
         */
        fun withMessage(message: Int) = apply {
            this.message = message
        }

        fun build(): AuthenticationRequest {
            return AuthenticationRequest(requestType, message, additionalExtras, intent)
        }
    }

    companion object {
        @JvmField
        val JUST_AUTHENTICATE = Builder().build()
    }
}

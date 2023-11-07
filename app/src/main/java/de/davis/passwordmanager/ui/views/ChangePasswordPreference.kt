package de.davis.passwordmanager.ui.views

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import de.davis.passwordmanager.R
import de.davis.passwordmanager.ui.auth.AuthenticationRequest
import de.davis.passwordmanager.ui.auth.RequestType
import de.davis.passwordmanager.ui.auth.createRequestAuthenticationIntent

class ChangePasswordPreference(context: Context, attributeSet: AttributeSet?) :
    Preference(context, attributeSet) {

    init {
        setTitle(R.string.update_main_password_info)
        intent = context.createRequestAuthenticationIntent(
            AuthenticationRequest.Builder(RequestType.AUTH_REQUEST_CHANGE_MAIN_PASSWORD).let {
                it.message = R.string.update_main_password_info
                it.build()
            }
        )
    }
}
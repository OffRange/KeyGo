@file:RequiresApi(Build.VERSION_CODES.O)

package de.davis.passwordmanager.services.autofill.builder

import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.service.autofill.RegexValidator
import android.service.autofill.SaveInfo
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.services.autofill.entities.AutofillForm
import de.davis.passwordmanager.services.autofill.entities.Identifier
import de.davis.passwordmanager.services.autofill.entities.UserCredentialsType
import de.davis.passwordmanager.services.autofill.entities.toSaveField
import de.davis.passwordmanager.services.autofill.extensions.get
import de.davis.passwordmanager.services.autofill.extensions.put

fun FillResponse.Builder.applySaveInfo(
    autofillForm: AutofillForm,
    isBrowser: Boolean,
    clientState: Bundle,
    requestId: Int
) {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isBrowser /*TODO maybe remove isBrowser and set minSDK to 27*/) {
        /*
         * On API 26 (Android Oreo), the Autofill service may not reliably receive the actual text
         * values from input fields in browsers. This is due to potential masking of sensitive data,
         * such as passwords, where the service might capture obfuscated characters (bullet dots)
         * instead. Since API 26 lacks support for certain Validator capabilities used to distinguish
         * between genuine text and these placeholders, the autofill process is bypassed to avoid
         * saving incorrect data.
         */
        return
    }

    autofillForm.autofillFields.map { it.toSaveField(requestId) }.forEach {
        clientState.put(saveField = it, url = autofillForm.url)
    }

    val type = clientState.get().userCredentialsTypes.fold(0) { acc, credentialType ->
        acc or when (credentialType) {
            is Identifier.Username -> SaveInfo.SAVE_DATA_TYPE_USERNAME
            is Identifier.Email -> SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS

            is UserCredentialsType.Password -> SaveInfo.SAVE_DATA_TYPE_PASSWORD

            else -> 0
        }
    }

    setSaveInfo(
        SaveInfo.Builder(
            type,
            autofillForm.autofillFields.map { it.autofillId }.toTypedArray()
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                autofillForm.autofillFields.find { it.userCredentialsType == UserCredentialsType.Password }
                    ?.let {
                        setValidator(
                            RegexValidator(it.autofillId, Regex("^[^\\u2022]*$").toPattern())
                        )
                    }
            }

            setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (autofillForm.autofillFields.none { it.userCredentialsType == UserCredentialsType.Password })
                    setFlags(SaveInfo.FLAG_DELAY_SAVE)
            }

            setClientState(clientState)
        }.build()
    )
}
package de.davis.keygo.autofill.presentation.dataset

import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.service.autofill.RegexValidator
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.autofill.presentation.model.FieldType

internal fun FillResponse.Builder.applySaveInfo(
    extraction: Extraction,
    clientInfo: Bundle,
    requestId: Int,
    inCompatibilityMode: Boolean,
) {
    val (updatedClientState, saveType) = clientInfo.updateState(requestId, extraction)
    val password = updatedClientState.getPasswordId()
    val username = updatedClientState.getUsernameId()
    val email = updatedClientState.getEmailId()

    val requiredIds = listOfNotNull(password, username, email).toTypedArray()

    val saveInfo = SaveInfo.Builder(saveType, requiredIds).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var flag = if (password == null) SaveInfo.FLAG_DELAY_SAVE else 0

            if (inCompatibilityMode)
                flag = flag or SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE

            println("SaveInfo flags: $flag")
            setFlags(flag)
        }

        password?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
                RegexValidator(it, "^\\p{ASCII}*$".toPattern())
                    .also(::setValidator)
        }
    }.build()

    setSaveInfo(saveInfo)
    setClientState(updatedClientState)
}

private const val KEY_REQUEST_ID = "requestId"
private const val KEY_PASSWORD_ID = "passwordId"
private const val KEY_USERNAME_ID = "usernameId"
private const val KEY_EMAIL_ID = "emailID"
private const val KEY_URL = "url"
private const val KEY_SAVE_TYPE = "saveType"

internal fun Bundle.updateState(
    requestId: Int,
    extraction: Extraction,
): Pair<Bundle, Int> {
    var saveType = getInt(KEY_SAVE_TYPE, 0)

    return Bundle(this).apply {
        putInt(KEY_REQUEST_ID, requestId)

        val credentialFields = extraction.getCredentialFields()
        credentialFields.find { it.type == FieldType.Credentials.Password }?.let {
            putParcelable(KEY_PASSWORD_ID, it.autofillId)
            saveType = saveType or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        }
        credentialFields.find { it.type == FieldType.Credentials.Username }?.let {
            putParcelable(KEY_USERNAME_ID, it.autofillId)
            saveType = saveType or SaveInfo.SAVE_DATA_TYPE_USERNAME
        }
        credentialFields.find { it.type == FieldType.Credentials.EMail }?.let {
            putParcelable(KEY_EMAIL_ID, it.autofillId)
            saveType = saveType or SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS
        }

        extraction.urls.firstOrNull()?.let {
            putString(KEY_URL, it)
        }

        putInt(KEY_SAVE_TYPE, saveType)
    } to saveType
}

@Suppress("DEPRECATION")
internal fun Bundle.getPasswordId(): AutofillId? = getParcelable(KEY_PASSWORD_ID)

@Suppress("DEPRECATION")
internal fun Bundle.getUsernameId(): AutofillId? = getParcelable(KEY_USERNAME_ID)

@Suppress("DEPRECATION")
internal fun Bundle.getEmailId(): AutofillId? = getParcelable(KEY_EMAIL_ID)
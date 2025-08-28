package de.davis.keygo.autofill.presentation.dataset

import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.service.autofill.RegexValidator
import android.service.autofill.SaveInfo
import android.util.Log
import androidx.core.os.BundleCompat
import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.autofill.presentation.model.FieldType
import de.davis.keygo.autofill.presentation.model.SaveInfoField

private const val TAG = "SaveInfoAppender"

internal fun FillResponse.Builder.applySaveInfo(
    extraction: Extraction,
    clientInfo: Bundle,
    requestId: Int,
    inCompatibilityMode: Boolean,
) {
    val (updatedClientState, saveType) = clientInfo.updateState(requestId, extraction)
    val password = updatedClientState.getPasswordField()
    val username = updatedClientState.getUsernameField()
    val email = updatedClientState.getEmailField()

    val requiredIds = listOfNotNull(password, username, email).map { it.autofillId }.toTypedArray()

    Log.d(
        TAG,
        "Applying Save Info:\n" +
                "- Request ID: $requestId\n" +
                "- Password ID: $password, Username ID: $username, Email ID: $email\n" +
                "- Save type: $saveType"
    )


    val saveInfo = SaveInfo.Builder(saveType, requiredIds).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var flag = if (password == null) SaveInfo.FLAG_DELAY_SAVE else 0

            if (inCompatibilityMode)
                flag = flag or SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE

            Log.d(TAG, "SaveInfo flags: $flag")
            setFlags(flag)
        }

        password?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
                RegexValidator(it.autofillId, "^\\p{ASCII}*$".toPattern())
                    .also(::setValidator)
        }
    }.build()

    setSaveInfo(saveInfo)
    setClientState(updatedClientState)
}

private const val KEY_PASSWORD_ID = "passwordId"
private const val KEY_USERNAME_ID = "usernameId"
private const val KEY_EMAIL_ID = "emailID"
private const val KEY_URL = "url"
private const val KEY_SAVE_TYPE = "saveType"

internal fun Bundle.updateState(
    requestId: Int,
    extraction: Extraction,
): Pair<Bundle, Int> {
    classLoader = SaveInfoField::class.java.classLoader
    var saveType = getInt(KEY_SAVE_TYPE, 0)

    return Bundle(this).apply {
        val credentialFields = extraction.getCredentialFields()
        credentialFields.find { it.type == FieldType.Credentials.Password }?.let {
            putParcelable(KEY_PASSWORD_ID, SaveInfoField(it.autofillId, requestId))
            saveType = saveType or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        }
        credentialFields.find { it.type == FieldType.Credentials.Username }?.let {
            putParcelable(KEY_USERNAME_ID, SaveInfoField(it.autofillId, requestId))
            saveType = saveType or SaveInfo.SAVE_DATA_TYPE_USERNAME
        }
        credentialFields.find { it.type == FieldType.Credentials.EMail }?.let {
            putParcelable(KEY_EMAIL_ID, SaveInfoField(it.autofillId, requestId))
            saveType = saveType or SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS
        }

        extraction.urls.firstOrNull()?.let {
            putString(KEY_URL, it)
        }

        putInt(KEY_SAVE_TYPE, saveType)
    } to saveType
}

internal fun Bundle.getPasswordField(): SaveInfoField? = getKeyGoParcelable(KEY_PASSWORD_ID)

internal fun Bundle.getUsernameField(): SaveInfoField? = getKeyGoParcelable(KEY_USERNAME_ID)

internal fun Bundle.getEmailField(): SaveInfoField? = getKeyGoParcelable(KEY_EMAIL_ID)

private inline fun <reified T> Bundle.getKeyGoParcelable(key: String): T? {
    classLoader = T::class.java.classLoader
    return BundleCompat.getParcelable(this, key, T::class.java)
}
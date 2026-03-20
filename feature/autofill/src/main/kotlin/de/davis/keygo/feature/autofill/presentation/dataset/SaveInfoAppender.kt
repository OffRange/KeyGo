package de.davis.keygo.feature.autofill.presentation.dataset

import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.service.autofill.RegexValidator
import android.service.autofill.SaveInfo
import android.util.Log
import androidx.core.os.BundleCompat
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormField
import de.davis.keygo.feature.autofill.presentation.model.FormType

private const val TAG = "SaveInfoAppender"

internal fun FillResponse.Builder.applySaveInfo(
    form: Form,
    clientInfo: Bundle,
    requestId: Int,
    inCompatibilityMode: Boolean,
) {
    val (updatedClientState, saveType) = clientInfo.updateState(requestId, form)

    val updatedForm = updatedClientState.getForm()
        ?: throw IllegalStateException("No form in client state")

    val requiredIds = updatedForm.fields.map { it.autofillId }.toTypedArray()

    Log.d(
        TAG,
        "Applied Save Info:\n" +
                "- Request ID: $requestId\n" +
                "- Current Form: $updatedForm\n" +
                "- Save type: $saveType"
    )


    val saveInfo = SaveInfo.Builder(saveType, requiredIds).apply {
        val password = updatedForm.getPasswordField()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var flag = password?.let { 0 } ?: SaveInfo.FLAG_DELAY_SAVE

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

private fun Form.getPasswordField(): FormField? {
    if (type != FormType.Credentials) return null

    return fields.find { it.type == FieldType.Credentials.Password }
}

private const val KEY_FORM = "form"
private const val KEY_SAVE_TYPE = "saveType"

internal fun Bundle.updateState(
    requestId: Int,
    form: Form,
): Pair<Bundle, Int> {
    classLoader = Form::class.java.classLoader
    var saveType = getInt(KEY_SAVE_TYPE, SaveInfo.SAVE_DATA_TYPE_GENERIC)

    val satisfiedForm = form.mapFields { it.copy(requestId = requestId) }
    val existingForm = getForm()
    val mergedForm = existingForm?.merge(satisfiedForm) ?: satisfiedForm

    return Bundle(this).apply {
        putParcelable(
            KEY_FORM,
            mergedForm
        )

        satisfiedForm.fields.forEach {
            if (it.requestId != requestId) return@forEach

            saveType = saveType or when (it.type) {
                FieldType.Credentials.Password -> SaveInfo.SAVE_DATA_TYPE_PASSWORD

                FieldType.Credentials.Username -> SaveInfo.SAVE_DATA_TYPE_USERNAME

                FieldType.Credentials.EMail -> SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS

                FieldType.TOTP,
                FieldType.Credentials.Phone,
                FieldType.Undefined -> SaveInfo.SAVE_DATA_TYPE_GENERIC
            }
        }

        putInt(KEY_SAVE_TYPE, saveType)
    } to saveType
}

private fun Form.merge(other: Form): Form {
    if (this.type != other.type)
        throw IllegalArgumentException("Cannot merge forms of different type")

    val mergedFields = (this.fields + other.fields)
        .filter { it.url == this.url } // only keep fields from the same URL
        .filter { it.type.includeInSaveInfo }
        .distinctBy { it.autofillId }

    return copy(fields = mergedFields)
}

internal fun Bundle.getForm(): Form? = getKeyGoParcelable(KEY_FORM)

private inline fun <reified T> Bundle.getKeyGoParcelable(key: String): T? {
    classLoader = T::class.java.classLoader
    return BundleCompat.getParcelable(this, key, T::class.java)
}
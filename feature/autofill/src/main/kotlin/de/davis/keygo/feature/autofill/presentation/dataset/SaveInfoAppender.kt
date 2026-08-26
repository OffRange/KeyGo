package de.davis.keygo.feature.autofill.presentation.dataset

import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.service.autofill.RegexValidator
import android.service.autofill.SaveInfo
import android.util.Log
import android.view.autofill.AutofillId
import androidx.core.os.BundleCompat
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormField
import de.davis.keygo.feature.autofill.presentation.model.FormType

private const val TAG = "SaveInfoAppender"

/**
 * Applies the save info for [form] to the response, if there is anything worth saving at all.
 *
 * The client state is updated and forwarded on every request, also when no save info is applied, so
 * that the fields collected on earlier requests stay available for the requests that follow.
 *
 * @return whether a save info was applied.
 */
internal fun FillResponse.Builder.applySaveInfo(
    form: Form,
    clientInfo: Bundle,
    requestId: Int,
    inCompatibilityMode: Boolean,
): Boolean {
    val (updatedClientState, saveType) = clientInfo.updateState(requestId, form)
    setClientState(updatedClientState)

    val savableForm = updatedClientState.getForm()
    if (savableForm == null || !savableForm.hasFields()) {
        Log.d(TAG, "Nothing to save for request $requestId - no save info applied")
        return false
    }

    val saveIds = savableForm.toSaveIds()
    Log.d(
        TAG,
        "Applied Save Info:\n" +
                "- Request ID: $requestId\n" +
                "- Current Form: $savableForm\n" +
                "- Save type: $saveType\n" +
                "- Required ids: ${saveIds.required}\n" +
                "- Optional ids: ${saveIds.optional}"
    )

    val saveInfo = SaveInfo.Builder(saveType, saveIds.required.toTypedArray()).apply {
        if (saveIds.optional.isNotEmpty())
            setOptionalIds(saveIds.optional.toTypedArray())

        val password = savableForm.getPasswordField()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var flag = password?.let { 0 } ?: SaveInfo.FLAG_DELAY_SAVE

            if (inCompatibilityMode)
                flag = flag or SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE

            Log.d(TAG, "SaveInfo flags: $flag")
            setFlags(flag)
        }

        password?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
                RegexValidator(it.autofillId, PASSWORD_REGEX)
                    .also(::setValidator)
        }
    }.build()

    setSaveInfo(saveInfo)
    return true
}

internal data class SaveIds(
    val required: List<AutofillId>,
    val optional: List<AutofillId>,
)

/**
 * Splits the fields of this form into required and optional save ids.
 *
 * Must only be called on a form that [has fields][Form.hasFields], the framework rejects a save
 * info without a single required id.
 */
internal fun Form.toSaveIds(): SaveIds {
    val password = getPasswordField()
        ?: return SaveIds(required = fields.map { it.autofillId }, optional = emptyList())

    return SaveIds(
        required = listOf(password.autofillId),
        optional = fields.map { it.autofillId } - password.autofillId,
    )
}

private fun Form.getPasswordField(): FormField? {
    if (type != FormType.Credentials) return null

    return fields.find { it.type == FieldType.Credentials.Password }
}

private val PASSWORD_REGEX = "^\\p{ASCII}*$".toPattern()
private const val KEY_FORM = "form"
private const val KEY_SAVE_TYPE = "saveType"

/**
 * Folds [form] into the save context carried by this client state and returns the updated state
 * together with the save type it accumulated.
 *
 * The save context only ever holds fields we are actually able to save, so a screen that has
 * nothing savable on it (a one time code, for example) leaves the state as it is: it neither
 * contributes fields nor invalidates the ones collected before it.
 */
internal fun Bundle.updateState(
    requestId: Int,
    form: Form,
): Pair<Bundle, Int> {
    classLoader = Form::class.java.classLoader
    val currentSaveType = getInt(KEY_SAVE_TYPE, SaveInfo.SAVE_DATA_TYPE_GENERIC)

    val savableForm = form
        .mapFields { it.copy(requestId = requestId) }
        .onlySavableFields()

    if (!savableForm.hasFields()) {
        Log.d(TAG, "No savable fields in request $requestId - keeping the save context as is")
        return Bundle(this) to currentSaveType
    }

    // A form of another type belongs to a screen we are no longer on, so it starts a new save
    // context instead of being merged into the stale one.
    val previousForm = getForm()?.takeIf { it.type == savableForm.type }
    val inheritedSaveType = if (previousForm != null) currentSaveType
    else SaveInfo.SAVE_DATA_TYPE_GENERIC

    val mergedForm = previousForm?.let(savableForm::mergeWith) ?: savableForm
    val saveType = inheritedSaveType or savableForm.saveDataType()

    return Bundle(this).apply {
        putParcelable(
            KEY_FORM,
            mergedForm
        )

        putInt(KEY_SAVE_TYPE, saveType)
    } to saveType
}

private fun Form.onlySavableFields() = copy(fields = fields.filter { it.type.includeInSaveInfo })

/**
 * Merges the fields collected by [previous] into this form. This form wins: it describes the screen
 * the request came from, so its url decides which of the older fields are still relevant.
 */
private fun Form.mergeWith(previous: Form): Form {
    val mergedFields = (previous.fields + fields)
        .filter { it.url == url /* only keep fields from the same URL */ }
        .distinctBy { it.autofillId }

    return copy(fields = mergedFields)
}

private fun Form.saveDataType() = fields.fold(SaveInfo.SAVE_DATA_TYPE_GENERIC) { saveType, field ->
    saveType or when (field.type) {
        FieldType.Credentials.Password -> SaveInfo.SAVE_DATA_TYPE_PASSWORD

        FieldType.Credentials.Username -> SaveInfo.SAVE_DATA_TYPE_USERNAME

        FieldType.Credentials.EMail -> SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS

        FieldType.TOTP,
        FieldType.Credentials.Phone,
        FieldType.Undefined -> SaveInfo.SAVE_DATA_TYPE_GENERIC
    }
}

internal fun Bundle.getForm(): Form? = getKeyGoParcelable(KEY_FORM)

private inline fun <reified T> Bundle.getKeyGoParcelable(key: String): T? {
    classLoader = T::class.java.classLoader
    return BundleCompat.getParcelable(this, key, T::class.java)
}

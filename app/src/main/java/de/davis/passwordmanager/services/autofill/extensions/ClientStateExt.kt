package de.davis.passwordmanager.services.autofill.extensions

import android.os.Bundle
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.services.autofill.entities.Identifier
import de.davis.passwordmanager.services.autofill.entities.SaveField
import de.davis.passwordmanager.services.autofill.entities.SaveForm
import de.davis.passwordmanager.services.autofill.entities.UserCredentialsType

private const val KEY_SAVE_FORM = "key_save_form"

fun Bundle.put(saveField: SaveField? = null, url: String? = null) {
    val saveForm = getParcelableCompat(KEY_SAVE_FORM, SaveForm::class.java) ?: SaveForm()
    val updatedSaveForm = when (saveField?.userCredentialsType) {
        is Identifier -> saveForm.copy(identifierSaveField = saveField)
        UserCredentialsType.Password -> saveForm.copy(passwordSaveField = saveField)
        else -> saveForm
    }.copy(url = url ?: saveForm.url)

    putParcelable(KEY_SAVE_FORM, updatedSaveForm)
}

fun Bundle.get(): SaveForm =
    getParcelableCompat(KEY_SAVE_FORM, SaveForm::class.java) ?: SaveForm()

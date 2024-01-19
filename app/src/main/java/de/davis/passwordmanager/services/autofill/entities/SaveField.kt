package de.davis.passwordmanager.services.autofill.entities

import android.os.Parcelable
import android.view.autofill.AutofillId
import kotlinx.parcelize.Parcelize

@Parcelize
data class SaveField(
    val autofillId: AutofillId,
    val userCredentialsType: UserCredentialsType,
    val requestId: Int
) : Parcelable

fun AutofillField.toSaveField(requestId: Int) =
    SaveField(autofillId, userCredentialsType, requestId)

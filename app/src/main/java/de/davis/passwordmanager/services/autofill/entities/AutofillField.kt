package de.davis.passwordmanager.services.autofill.entities

import android.os.Parcelable
import android.view.autofill.AutofillId
import kotlinx.parcelize.Parcelize

@Parcelize
data class AutofillField(
    val autofillId: AutofillId,
    val userCredentialsType: UserCredentialsType
) : Parcelable
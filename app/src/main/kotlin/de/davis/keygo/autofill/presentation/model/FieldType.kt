package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface FieldType : Parcelable {

    @Parcelize
    sealed interface Credentials : FieldType {
        data object Username : Credentials
        data object EMail : Credentials
        data object Phone : Credentials

        data object Password : Credentials
    }

    data object Undefined : FieldType
}
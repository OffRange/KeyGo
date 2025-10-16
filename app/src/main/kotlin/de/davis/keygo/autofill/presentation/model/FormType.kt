package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface FormType : Parcelable {

    data object Credentials : FormType
    data object TOTP : FormType
}
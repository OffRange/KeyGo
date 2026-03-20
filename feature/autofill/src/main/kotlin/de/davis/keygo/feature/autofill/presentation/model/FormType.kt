package de.davis.keygo.feature.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal sealed interface FormType : Parcelable {

    data object Credentials : FormType
    data object TOTP : FormType
}
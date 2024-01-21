package de.davis.passwordmanager.services.autofill.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface UserCredentialsType : Parcelable {

    @Parcelize
    data object Password : UserCredentialsType

    @Parcelize
    data object Unidentified : UserCredentialsType
}

sealed interface Identifier : UserCredentialsType {

    @Parcelize
    data object Username : Identifier

    @Parcelize
    data object Email : Identifier

    @Parcelize
    data object PhoneNumber : Identifier
}
package de.davis.passwordmanager.services.autofill.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface UserCredentialsType : Parcelable {
    @Parcelize
    data object Identifier : UserCredentialsType

    @Parcelize
    data object Password : UserCredentialsType

    @Parcelize
    data object Unidentified : UserCredentialsType
}
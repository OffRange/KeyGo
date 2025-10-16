package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.reflect.KClass

@Parcelize
sealed interface FieldType : Parcelable {

    val group: KClass<out FieldType>
    val includeInSaveInfo: Boolean
        get() = true

    @Parcelize
    sealed interface Credentials : FieldType {
        override val group: KClass<out FieldType>
            get() = Credentials::class

        data object Username : Credentials
        data object EMail : Credentials
        data object Phone : Credentials

        data object Password : Credentials
    }


    @Parcelize
    data object TOTP : FieldType {
        override val group: KClass<out FieldType>
            get() = TOTP::class

        override val includeInSaveInfo: Boolean
            get() = false
    }

    @Parcelize
    data object Undefined : FieldType {

        override val group: KClass<out FieldType>
            get() = Undefined::class
    }
}
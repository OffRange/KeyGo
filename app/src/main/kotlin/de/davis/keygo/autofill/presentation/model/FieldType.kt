package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.reflect.KClass

@Parcelize
sealed interface FieldType : Parcelable {

    val group: KClass<out FieldType>

    @Parcelize
    sealed interface Credentials : FieldType {
        override val group: KClass<out FieldType>
            get() = Credentials::class

        data object Username : Credentials
        data object EMail : Credentials
        data object Phone : Credentials

        data object Password : Credentials
    }

    data object Undefined : FieldType {

        override val group: KClass<out FieldType>
            get() = Undefined::class
    }
}
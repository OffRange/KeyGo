package de.davis.keygo.feature.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class Form(
    val type: FormType,
    val fields: List<FormField>,
    val url: String?,
    val isBrowser: Boolean,
    val appPackageName: String,
    val isSuspicious: Boolean
) : Parcelable {

    fun hasFields() = fields.isNotEmpty()

    fun mapFields(transform: (FormField) -> FormField) =
        copy(fields = fields.map(transform))
}
package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Form(
    val type: FormType,
    val fields: List<FormField>,
    val urls: Set<String>
) : Parcelable {

    fun hasFields() = fields.isNotEmpty()

    fun mapFields(transform: (FormField) -> FormField) =
        copy(fields = fields.map(transform))
}
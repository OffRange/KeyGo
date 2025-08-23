package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Extraction(
    val fields: List<ExtractedField>,
    val urls: Set<String>
) : Parcelable {
    fun hasFields(): Boolean {
        return fields.isNotEmpty()
    }

    fun getCredentialFields(): List<ExtractedField> =
        fields.filter { it.type is FieldType.Credentials }
}
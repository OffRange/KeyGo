package de.davis.keygo.autofill.presentation.model

import de.davis.keygo.autofill.domain.model.FieldType

data class Extraction(
    val fields: List<ExtractedField>,
    val urls: Set<String>
) {
    fun hasFields(): Boolean {
        return fields.isNotEmpty()
    }

    fun getCredentialFields(): List<ExtractedField> =
        fields.filter { it.type is FieldType.Credentials }
}
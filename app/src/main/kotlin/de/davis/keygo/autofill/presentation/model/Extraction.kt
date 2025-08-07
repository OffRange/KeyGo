package de.davis.keygo.autofill.presentation.model

data class Extraction(
    val fields: List<ExtractedField>,
    val urls: Set<String>
) {
    fun hasFields(): Boolean {
        return fields.isNotEmpty()
    }
}
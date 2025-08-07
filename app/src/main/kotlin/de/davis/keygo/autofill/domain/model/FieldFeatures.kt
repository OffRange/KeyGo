package de.davis.keygo.autofill.domain.model

data class FieldFeatures(
    val autofillHints: Set<String>,
    val htmlAttributes: Map<String, String>,
    val tokens: Set<String>,
)
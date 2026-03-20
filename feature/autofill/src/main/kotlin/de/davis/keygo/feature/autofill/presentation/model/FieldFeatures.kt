package de.davis.keygo.feature.autofill.presentation.model

internal data class FieldFeatures(
    val autofillHints: Set<String>,
    val htmlAttributes: Map<String, String>,
    val tokens: Set<String>,
)
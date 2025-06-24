package de.davis.keygo.viewing.presentation.model

enum class FieldType(val isSensitive: Boolean = false) {
    Name,
    Password(isSensitive = true),
    Username,
    Website,
    Note
}
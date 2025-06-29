package de.davis.keygo.item.viewing.presentation.password.model

enum class FieldType(val isSensitive: Boolean = false) {
    Name,
    Password(isSensitive = true),
    Username,
    Website,
    Note
}
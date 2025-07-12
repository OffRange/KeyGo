package de.davis.keygo.item.viewing.presentation.password.model

enum class FieldType(val isSensitive: Boolean = false) {
    Name,
    Password(isSensitive = true),
    Totp,
    Username,
    Website,
    Note
}
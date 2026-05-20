package de.davis.keygo.feature.item.core.presentation.login.model

enum class FieldType(val isSensitive: Boolean = false) {
    Name,
    Password(isSensitive = true),
    Totp,
    Username,
    Domain,
    Tag,
    Note,
}

package de.davis.keygo.autofill.domain.model

sealed interface FieldType {
    sealed interface Identifier : FieldType {
        data object Username : Identifier
        data object EMail : Identifier
        data object Phone : Identifier
    }

    data object Password : FieldType

    data object Undefined : FieldType
}
package de.davis.keygo.autofill.domain.model

sealed interface FieldType {
    sealed interface Credentials : FieldType {
        data object Username : Credentials
        data object EMail : Credentials
        data object Phone : Credentials

        data object Password : FieldType
    }

    data object Undefined : FieldType
}
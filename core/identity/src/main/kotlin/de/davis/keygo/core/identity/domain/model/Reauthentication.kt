package de.davis.keygo.core.identity.domain.model

sealed interface Reauthentication {

    data class Password(val currentPassword: String) : Reauthentication

    data object Biometric : Reauthentication
}

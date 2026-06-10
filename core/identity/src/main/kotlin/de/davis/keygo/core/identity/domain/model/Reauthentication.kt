package de.davis.keygo.core.identity.domain.model

sealed interface Reauthentication {

    data class Password(val currentPassword: String) : Reauthentication

    class Biometric(val recoveredArk: ByteArray) : Reauthentication
}

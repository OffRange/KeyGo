package de.davis.keygo.core.security.domain

import de.davisalessandro.keygo.rust.KeyWrapException

sealed interface SessionError {

    data object Locked : SessionError
    data object WrongPassword : SessionError
    data class Derivation(val message: String) : SessionError
    data class KeyWrap(val cause: KeyWrapException) : SessionError
}

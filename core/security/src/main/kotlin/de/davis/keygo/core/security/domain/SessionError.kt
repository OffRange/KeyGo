package de.davis.keygo.core.security.domain

/** Why a [Session] call did not produce a result. Callers map these onto their own domain errors. */
sealed interface SessionError {
    /** No ARK in custody: the session was never unlocked, or it has ended. */
    data object Locked : SessionError

    /** The supplied password did not unwrap the stored ARK. */
    data object WrongPassword : SessionError

    /** Argon2 could not derive a KEK. */
    data class Derivation(val message: String) : SessionError

    /** Wrapping or unwrapping failed: wrong key, wrong AAD, or corrupted data. */
    data class KeyWrap(val message: String) : SessionError
}

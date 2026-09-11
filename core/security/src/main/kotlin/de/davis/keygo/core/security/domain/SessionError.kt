package de.davis.keygo.core.security.domain

import de.davisalessandro.keygo.rust.KeyWrapException

/** Why a [Session] call did not produce a result. Callers map these onto their own domain errors. */
sealed interface SessionError {
    /** No ARK in custody: the session was never unlocked, or it has ended. */
    data object Locked : SessionError

    /**
     * The supplied password does not open the ARK this session holds: the stored blob did not
     * unwrap, or it unwrapped to a different ARK. Only [Session.verifyPassword] reports this;
     * [Session.unlockWithPassword] reports the underlying [KeyWrap] failure instead.
     */
    data object WrongPassword : SessionError

    /** Argon2 could not derive a KEK. */
    data class Derivation(val message: String) : SessionError

    /**
     * Wrapping or unwrapping failed: wrong key, wrong AAD, or corrupted data. [cause] is Rust's own
     * error, kept so a truncated blob stays distinguishable from a wrong key.
     */
    data class KeyWrap(val cause: KeyWrapException) : SessionError
}

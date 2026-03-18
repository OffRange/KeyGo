package de.davis.keygo.auth.presentation.model

import de.davis.keygo.core.security.domain.model.CryptographicMode

internal sealed interface BiometricRequest {

    val cryptoMode: CryptographicMode

    data class CreateAccess(val password: String) : BiometricRequest {
        override val cryptoMode: CryptographicMode
            get() = CryptographicMode.Wrap
    }

    data object Login : BiometricRequest {
        override val cryptoMode: CryptographicMode
            get() = CryptographicMode.Unwrap
    }
}
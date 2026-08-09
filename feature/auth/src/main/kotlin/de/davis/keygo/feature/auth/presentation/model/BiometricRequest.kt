package de.davis.keygo.feature.auth.presentation.model

import de.davis.keygo.core.security.domain.model.CryptographicMode

internal sealed interface BiometricRequest {

    val cryptoMode: CryptographicMode

    data object Login : BiometricRequest {
        override val cryptoMode: CryptographicMode
            get() = CryptographicMode.Unwrap
    }
}
package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.presentation.BiometricCryptoController
import de.davis.keygo.core.util.Result

interface BiometricUnlockAdapter {

    suspend fun BiometricCryptoController.requestUnlockVault(): Result<Unit, UnlockError>
}

inline fun BiometricUnlockAdapter.useAdapter(
    block: BiometricUnlockAdapter.() -> Result<Unit, UnlockError>
): Result<Unit, UnlockError> = with(this) {
    block()
}
package de.davis.keygo.feature.account.presentation.setup.mapper

import de.davis.keygo.core.identity.domain.model.VerifyPasswordError
import de.davis.keygo.core.sync.domain.model.RegisterError
import de.davis.keygo.feature.account.presentation.setup.model.PasswordCheck

internal fun RegisterError.toPasswordCheck(): PasswordCheck = when (this) {
    RegisterError.Password(VerifyPasswordError.IncorrectPassword) -> PasswordCheck.Incorrect
    is RegisterError.Sync -> PasswordCheck.RegisterFailed(error)
    else -> PasswordCheck.Failed
}

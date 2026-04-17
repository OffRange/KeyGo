package de.davis.keygo.core.identity.domain.model

import java.util.UUID

data class Account(
    val id: UUID,
    val displayName: String,
    val passwordWrappedArk: PasswordWrappedArk,
    val biometricWrappedArk: BiometricWrappedArk?,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

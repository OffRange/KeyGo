package de.davis.keygo.core.item.domain.model

class PasskeyMetadata(
    val passwordUsername: String?,
    val vaultName: String,
    val user: PasskeyUser,
    val credentialId: ByteArray
)
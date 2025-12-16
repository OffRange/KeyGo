package de.davis.keygo.rust.passkey.model

class KeyGoRegistrationResponse(
    val responseJson: String,
    val rpId: String,
    val userName: String,
    val userDisplayName: String,
    val credentialId: ByteArray,
    val privateKey: ByteArray
)
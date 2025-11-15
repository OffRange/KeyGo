package de.davis.keygo.rust.passkey.model

data class KeyGoRegistrationResponse(
    val responseJson: String,
    val credentialId: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyGoRegistrationResponse

        if (responseJson != other.responseJson) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = responseJson.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class Passkey(
    val credentialId: ByteArray,
    val rp: String,
    val privateKey: SecretData<String>,
    val loginId: ItemId,
    val user: PasskeyUser
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Passkey

        if (loginId != other.loginId) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (rp != other.rp) return false
        if (privateKey != other.privateKey) return false
        if (user != other.user) return false

        return true
    }

    override fun hashCode(): Int {
        var result = loginId.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + rp.hashCode()
        result = 31 * result + privateKey.hashCode()
        result = 31 * result + user.hashCode()
        return result
    }
}
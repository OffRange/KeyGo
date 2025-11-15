package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class Passkey(
    val credentialId: ByteArray,
    val privateKey: SecretData<String>,
    val passwordId: ItemId,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Passkey

        if (!credentialId.contentEquals(other.credentialId)) return false
        if (privateKey != other.privateKey) return false
        if (passwordId != other.passwordId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.contentHashCode()
        result = 31 * result + privateKey.hashCode()
        result = 31 * result + passwordId.hashCode()
        return result
    }
}

package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.model.PasskeyRef
import de.davis.keygo.core.item.domain.model.PasskeyUser

class NewPasskey(
    val credentialId: ByteArray,
    val rp: String,
    val user: PasskeyUser,
    val privateKey: ByteArray,
) {
    val ref: PasskeyRef
        get() = PasskeyRef(credentialId = credentialId, rp = rp)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NewPasskey

        if (!credentialId.contentEquals(other.credentialId)) return false
        if (rp != other.rp) return false
        if (user != other.user) return false
        if (!privateKey.contentEquals(other.privateKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.contentHashCode()
        result = 31 * result + rp.hashCode()
        result = 31 * result + user.hashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }

    override fun toString(): String = "NewPasskey(rp=$rp)"
}

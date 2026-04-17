package de.davis.keygo.core.identity.domain.model

data class PasswordWrappedArk(
    val key: ByteArray,
    val keyIV: ByteArray,
    val salt: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasswordWrappedArk

        if (!key.contentEquals(other.key)) return false
        if (!keyIV.contentEquals(other.keyIV)) return false
        if (!salt.contentEquals(other.salt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + keyIV.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        return result
    }

    fun isValid(): Boolean = key.isNotEmpty() && keyIV.isNotEmpty() && salt.isNotEmpty()
}

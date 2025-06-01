package de.davis.keygo.auth.domain.model

data class PasswordWrappedKeyData(
    val wrappedKey: ByteArray,
    val iv: ByteArray,
    val salt: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasswordWrappedKeyData

        if (!wrappedKey.contentEquals(other.wrappedKey)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!salt.contentEquals(other.salt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = wrappedKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        return result
    }

    fun isValid(): Boolean = wrappedKey.isNotEmpty() && iv.isNotEmpty() && salt.isNotEmpty()
}
package de.davis.keygo.auth.domain.model

data class BiometricWrappedKeyData(
    val wrappedKey: ByteArray,
    val iv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiometricWrappedKeyData

        if (!wrappedKey.contentEquals(other.wrappedKey)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = wrappedKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }

    fun isValid(): Boolean = wrappedKey.isNotEmpty() && iv.isNotEmpty()
}

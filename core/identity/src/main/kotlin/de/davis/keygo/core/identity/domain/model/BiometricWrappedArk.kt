package de.davis.keygo.core.identity.domain.model

data class BiometricWrappedArk(
    val key: ByteArray,
    val keyIV: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BiometricWrappedArk

        if (!key.contentEquals(other.key)) return false
        if (!keyIV.contentEquals(other.keyIV)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + keyIV.contentHashCode()
        return result
    }

    fun isValid(): Boolean = key.isNotEmpty() && keyIV.isNotEmpty()
}

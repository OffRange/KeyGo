package de.davis.keygo.core.security.domain.model

data class CiphertextData(
    val bytes: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CiphertextData

        if (!bytes.contentEquals(other.bytes)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
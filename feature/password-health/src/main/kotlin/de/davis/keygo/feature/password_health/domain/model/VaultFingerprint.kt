package de.davis.keygo.feature.password_health.domain.model

import java.security.MessageDigest

class VaultFingerprint(val value: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultFingerprint

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    companion object {

        fun of(fingerprints: Collection<HealthFingerprint>): VaultFingerprint {
            val digest = MessageDigest.getInstance("SHA-256")
            fingerprints
                .map { it.value }
                .sortedWith(UnsignedBytes)
                .forEach(digest::update)

            return VaultFingerprint(digest.digest())
        }

        // Arrays.compareUnsigned would do, but only exists from API 33.
        private object UnsignedBytes : Comparator<ByteArray> {
            override fun compare(a: ByteArray, b: ByteArray): Int {
                for (i in 0 until minOf(a.size, b.size)) {
                    val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
                    if (diff != 0) return diff
                }
                return a.size - b.size
            }
        }
    }
}

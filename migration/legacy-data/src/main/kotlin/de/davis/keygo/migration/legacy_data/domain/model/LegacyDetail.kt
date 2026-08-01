package de.davis.keygo.migration.legacy_data.domain.model

/** The decoded contents of a v1 `SecureElement.data` blob, before any v2 types are involved. */
internal sealed interface LegacyDetail {

    /** [password] is still encrypted: v1 nested a second layer of the same AES-GCM inside the JSON. */
    data class Password(
        val username: String?,
        val origin: String?,
        val password: ByteArray?,
        val strength: LegacyStrength?,
    ) : LegacyDetail {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Password

            if (username != other.username) return false
            if (origin != other.origin) return false
            if (!password.contentEquals(other.password)) return false
            if (strength != other.strength) return false

            return true
        }

        override fun hashCode(): Int {
            var result = username?.hashCode() ?: 0
            result = 31 * result + (origin?.hashCode() ?: 0)
            result = 31 * result + (password?.contentHashCode() ?: 0)
            result = 31 * result + (strength?.hashCode() ?: 0)
            return result
        }
    }

    data class CreditCard(
        val firstName: String?,
        val lastName: String?,
        val cardNumber: String?,
        val cvv: String?,
        val expirationDate: String?,
    ) : LegacyDetail
}

/** v1's `type` discriminator, which decides which [LegacyDetail] a decoded blob becomes. */
internal const val LEGACY_TYPE_PASSWORD = 0x1
internal const val LEGACY_TYPE_CREDIT_CARD = 0x11

package de.davis.keygo.migration.legacy_data.domain.model

/** v1's `Strength`, in v1's declaration order. The ordinal is load-bearing: the older JSON form
 *  stored it as `{"type": ordinal}`. */
internal enum class LegacyStrength {
    RIDICULOUS,
    WEAK,
    MODERATE,
    STRONG,
    VERY_STRONG,
}

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
            if (other !is Password) return false

            if (username != other.username) return false
            if (origin != other.origin) return false
            if (password != null) {
                if (other.password == null) return false
                if (!password.contentEquals(other.password)) return false
            } else if (other.password != null) return false
            return strength == other.strength
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

/** A single v1 row, decrypted and parsed, with its user tags already filtered. */
internal data class LegacyItem(
    val legacyId: Long,
    val title: String,
    val favorite: Boolean,
    val createdAt: Long?,
    val modifiedAt: Long?,
    val tags: Set<String>,
    val detail: LegacyDetail,
)

internal const val LEGACY_TYPE_PASSWORD = 0x1
internal const val LEGACY_TYPE_CREDIT_CARD = 0x11
internal const val LEGACY_TAG_PREFIX = "elementType:"

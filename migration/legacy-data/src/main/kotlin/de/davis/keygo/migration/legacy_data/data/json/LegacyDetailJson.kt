package de.davis.keygo.migration.legacy_data.data.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors what GSON wrote for `PasswordDetails` and `CreditCardDetails`. Every field is optional
 * because the two shapes share one object and because v1 omitted nulls.
 *
 * `strength` stays a raw [JsonElement] so the parser can accept both the enum-name string and the
 * older `{"type": ordinal}` object without a custom serializer.
 */
@Serializable
internal data class LegacyDetailJson(
    val type: Int? = null,
    val username: String? = null,
    val origin: String? = null,
    val password: ByteArray? = null,
    val strength: JsonElement? = null,
    val cardholder: LegacyNameJson? = null,
    val expirationDate: String? = null,
    val cardNumber: String? = null,
    val cvv: String? = null,
) {

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

@Serializable
internal data class LegacyNameJson(
    val firstName: String? = null,
    val lastName: String? = null,
)

package de.davis.keygo.legacy_migration.data.json

import de.davis.keygo.legacy_migration.domain.model.LEGACY_TYPE_CREDIT_CARD
import de.davis.keygo.legacy_migration.domain.model.LEGACY_TYPE_PASSWORD
import de.davis.keygo.legacy_migration.domain.model.LegacyDetail
import de.davis.keygo.legacy_migration.domain.model.LegacyStrength
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.koin.core.annotation.Single

@Single
internal class LegacyDetailParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Returns null for anything we cannot make sense of; the caller records that as a row failure. */
    fun parse(decrypted: ByteArray): LegacyDetail? {
        if (decrypted.isEmpty()) return null

        val parsed = runCatching {
            json.decodeFromString<LegacyDetailJson>(decrypted.decodeToString())
        }.getOrNull() ?: return null

        return when (parsed.type) {
            LEGACY_TYPE_PASSWORD -> LegacyDetail.Password(
                username = parsed.username,
                origin = parsed.origin,
                password = parsed.password,
                strength = parseStrength(parsed.strength),
            )

            LEGACY_TYPE_CREDIT_CARD -> LegacyDetail.CreditCard(
                firstName = parsed.cardholder?.firstName,
                lastName = parsed.cardholder?.lastName,
                cardNumber = parsed.cardNumber,
                cvv = parsed.cvv,
                expirationDate = parsed.expirationDate,
            )

            else -> null
        }
    }

    private fun parseStrength(element: JsonElement?): LegacyStrength? =
        when (element) {
            null -> null
            // A safe cast rather than `jsonPrimitive`, which throws when `type` holds an object or
            // an array. Nothing v1 wrote looks like that, but a throw here would escape `parse` and
            // turn one odd row into a crash instead of a skipped row.
            is JsonObject -> (element["type"] as? JsonPrimitive)
                ?.intOrNull
                ?.let(LegacyStrength.entries::getOrNull)

            is JsonPrimitive -> LegacyStrength.entries.firstOrNull { it.name == element.content }
            else -> null
        }
}

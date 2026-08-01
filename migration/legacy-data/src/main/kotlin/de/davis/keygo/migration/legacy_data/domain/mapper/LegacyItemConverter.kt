package de.davis.keygo.migration.legacy_data.domain.mapper

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.toYearMonthOrNull
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.migration.legacy_data.domain.crypto.LegacyCipher
import de.davis.keygo.migration.legacy_data.domain.model.LegacyDetail
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyStrength
import org.koin.core.annotation.Single
import javax.crypto.SecretKey
import kotlin.time.Clock
import kotlin.time.Instant

@Single
internal class LegacyItemConverter(
    private val cipher: LegacyCipher,
    private val registrableDomainResolver: RegistrableDomainResolver,
) {

    /**
     * Builds the v2 item for one v1 row, encrypting each secret under the supplied scope's item key.
     *
     * Returns null only when v1's nested password blob fails to decrypt. Dropping the password and
     * keeping the login would hand the user an entry that looks migrated but silently lost its
     * secret, so the whole row is reported as a failure and left in the legacy database instead.
     */
    context(scope: CryptographicScope)
    suspend fun convert(
        item: LegacyItem,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
        legacyKey: SecretKey,
    ): Item? = when (val detail = item.detail) {
        is LegacyDetail.Password ->
            convertPassword(item, detail, itemId, vaultId, keyInformation, legacyKey)

        is LegacyDetail.CreditCard -> convertCard(item, detail, itemId, vaultId, keyInformation)
    }

    context(scope: CryptographicScope)
    private suspend fun convertPassword(
        item: LegacyItem,
        detail: LegacyDetail.Password,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
        legacyKey: SecretKey,
    ): Login? {
        val credential = detail.password?.let { encrypted ->
            val plaintext = cipher.decrypt(encrypted, legacyKey)?.decodeToString() ?: return null
            PasswordCredential(
                secret = PasswordSecret.encrypt(plaintext),
                score = detail.strength.toPasswordScore(),
            )
        }

        return Login(
            id = itemId,
            username = detail.username?.trim()?.takeIf { it.isNotEmpty() },
            domainInfos = detail.origin
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { origin ->
                    setOf(
                        DomainInfo(
                            loginId = itemId,
                            value = origin,
                            eTLD1 = registrableDomainResolver.resolve(origin),
                        ),
                    )
                }
                .orEmpty(),
            passwordCredential = credential,
            totp = null,
            vaultId = vaultId,
            name = item.title,
            keyInformation = keyInformation,
            tags = item.toTags(),
            note = null,
            pinned = item.favorite,
            timestamp = item.toTimestamp(),
        )
    }

    context(scope: CryptographicScope)
    private suspend fun convertCard(
        item: LegacyItem,
        detail: LegacyDetail.CreditCard,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): CreditCard = CreditCard(
        id = itemId,
        vaultId = vaultId,
        name = item.title,
        keyInformation = keyInformation,
        tags = item.toTags(),
        note = null,
        pinned = item.favorite,
        holder = detail.fullName(),
        // v1 stored whatever the user typed, separators included; v2's own entry form only ever
        // stores digits (enforced by CardNumberInputTransformation), so migration normalizes here
        // too rather than handing the rest of v2 a card number shape it never otherwise produces.
        cardNumber = detail.cardNumber
            ?.filter(Char::isDigit)
            ?.takeIf { it.isNotEmpty() }
            ?.let { CreditCard.CardNumber.encrypt(it) },
        cvv = detail.cvv
            ?.takeIf { it.isNotBlank() }
            ?.let { CreditCard.CVV.encrypt(it) },
        expirationDate = detail.expirationDate?.toYearMonthOrNull(),
        timestamp = item.toTimestamp(),
    )

    /**
     * v1 and v2 both score with nbvcxz over the same `basicScore`: v1 stored
     * `Strength.entries[basicScore]`, v2 stores `PasswordScore(basicScore + 1)`. The mapping is
     * therefore exact and needs no re-estimation, which also keeps a migration of hundreds of items
     * from running nbvcxz hundreds of times.
     */
    private fun LegacyStrength?.toPasswordScore(): PasswordScore = when (this) {
        LegacyStrength.RIDICULOUS -> PasswordScore.Ridiculous
        LegacyStrength.WEAK -> PasswordScore.Weak
        LegacyStrength.MODERATE -> PasswordScore.Moderate
        LegacyStrength.STRONG -> PasswordScore.Strong
        LegacyStrength.VERY_STRONG -> PasswordScore.Excellent
        null -> PasswordScore.None
    }

    /** Mirrors v1's `Name.getFullName`, which returned null unless both halves were present. */
    private fun LegacyDetail.CreditCard.fullName(): String? =
        if (firstName == null || lastName == null) null else "$firstName $lastName"

    private fun LegacyItem.toTags(): Set<Tag> = tags.mapNotNull(Tag::of).toSet()

    private fun LegacyItem.toTimestamp(): Timestamp = Timestamp(
        createdAt = createdAt?.let(Instant::fromEpochMilliseconds) ?: Clock.System.now(),
        modifiedAt = modifiedAt?.let(Instant::fromEpochMilliseconds),
    )

}

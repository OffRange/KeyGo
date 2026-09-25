package de.davis.keygo.feature.password_health.domain.report

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.feature.password_health.domain.model.HealthFingerprint
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import java.util.UUID

internal val VAULT_ID: UUID = UUID(1L, 0L)

internal fun id(n: Int): ItemId = UUID(0L, n.toLong())

/** A login whose password [FakeCryptographicScopeProvider] decrypts back to [password]. */
internal fun login(
    id: ItemId,
    password: String = "password-$id",
    score: PasswordScore = PasswordScore.Strong,
) = Login(
    id = id,
    username = "user-$id",
    domainInfos = emptySet(),
    passwordCredential = PasswordCredential(
        secret = PasswordSecret(
            EncryptedPayload(
                ciphertext = FakeCryptographicScopeProvider.transform(
                    password.encodeToByteArray(),
                ),
                iv = FakeCryptographicScopeProvider.IV,
            ),
        ),
        score = score,
    ),
    totp = null,
    passkeys = emptySet(),
    vaultId = VAULT_ID,
    name = "login-$id",
    keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    timestamp = Timestamp(),
    note = null,
    pinned = false,
)

internal fun fingerprint(id: ItemId) = HealthFingerprint(id.toString().encodeToByteArray())

internal fun storedItem(
    id: ItemId,
    score: PasswordScore? = null,
    breachOccurrences: Int = 0,
    unreadable: Boolean = false,
) = StoredHealthItem(
    id = id,
    fingerprint = fingerprint(id),
    score = score,
    breachOccurrences = breachOccurrences,
    unreadable = unreadable,
)

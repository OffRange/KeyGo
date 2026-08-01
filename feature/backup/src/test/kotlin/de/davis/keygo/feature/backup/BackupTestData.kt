package de.davis.keygo.feature.backup

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyUser
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davisalessandro.keygo.rust.BackupCard
import de.davisalessandro.keygo.rust.BackupLogin
import de.davisalessandro.keygo.rust.BackupVault
import java.time.YearMonth

/** Encrypts [plaintext] the same way [FakeCryptographicScopeProvider]'s scope decrypts (XOR). */
fun secretPayload(plaintext: String) = EncryptedPayload(
    ciphertext = FakeCryptographicScopeProvider.transform(plaintext.encodeToByteArray()),
    iv = FakeCryptographicScopeProvider.IV,
)

private val emptyKey get() = KeyInformation(byteArrayOf(), byteArrayOf())

fun testVault(
    id: VaultId = newVaultId(),
    name: String,
    icon: Vault.Icon = Vault.Icon.Default,
) = Vault(
    id = id,
    name = name,
    keyInformation = emptyKey,
    icon = icon,
)

/** A vault as it appears inside a backup document. [icon] is blank unless a test is about icons. */
fun backupVault(
    name: String,
    logins: List<BackupLogin> = emptyList(),
    cards: List<BackupCard> = emptyList(),
    icon: String = "",
) = BackupVault(name = name, icon = icon, logins = logins, cards = cards)

fun testLogin(
    vaultId: VaultId,
    id: ItemId = newItemId(),
    name: String,
    username: String? = null,
    password: String? = null,
    totpSecret: String? = null,
    websites: Set<String> = emptySet(),
    tags: Set<String> = emptySet(),
    note: String? = null,
    passkeyRPs: Set<String> = emptySet(),
) = Login(
    id = id,
    vaultId = vaultId,
    name = name,
    username = username,
    domainInfos = websites.map { DomainInfo(loginId = id, value = it, eTLD1 = null) }.toSet(),
    passwordCredential = password?.let {
        PasswordCredential(secret = PasswordSecret(secretPayload(it)), score = PasswordScore.Strong)
    },
    totp = totpSecret?.let { Totp(loginId = id, secret = Totp.Secret(secretPayload(it))) },
    passkeyRPs = passkeyRPs,
    keyInformation = emptyKey,
    tags = tags.mapNotNull { Tag.of(it) }.toSet(),
    note = note,
    pinned = false,
)

fun testPasskey(
    loginId: ItemId,
    rp: String,
    privateKey: String,
    credentialId: ByteArray = rp.encodeToByteArray(),
    userName: String = "alice",
    userDisplayName: String = "Alice",
) = Passkey(
    credentialId = credentialId,
    rp = rp,
    privateKey = Passkey.PrivateKey(secretPayload(privateKey)),
    loginId = loginId,
    user = PasskeyUser(name = userName, displayName = userDisplayName),
)

fun testCard(
    vaultId: VaultId,
    id: ItemId = newItemId(),
    name: String,
    holder: String? = null,
    number: String? = null,
    cvv: String? = null,
    expiration: YearMonth? = null,
    note: String? = null,
) = CreditCard(
    id = id,
    vaultId = vaultId,
    name = name,
    keyInformation = emptyKey,
    tags = emptySet(),
    note = note,
    pinned = false,
    holder = holder,
    cardNumber = number?.let { CreditCard.CardNumber(secretPayload(it)) },
    cvv = cvv?.let { CreditCard.CVV(secretPayload(it)) },
    expirationDate = expiration,
)

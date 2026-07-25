package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.feature.item.core.domain.model.UpsertCreditCard
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davisalessandro.keygo.rust.BackupCard
import de.davisalessandro.keygo.rust.BackupLogin
import de.davisalessandro.keygo.rust.BackupVault

/**
 * Anything the icon set no longer covers falls back to the default rather than failing the import:
 * a backup written by a build that knows an icon this one does not is still worth restoring.
 */
internal fun BackupVault.toVaultIcon(): Vault.Icon =
    Vault.Icon.entries.firstOrNull { it.name == icon } ?: Vault.Icon.Default

internal fun BackupLogin.toUpsertLogin(vaultId: VaultId): UpsertLogin = UpsertLogin.create(
    vaultId = vaultId,
    name = title,
    password = password,
    totpUriOrSecret = totpSecret,
    username = username,
    domains = website?.let { setOf(DomainInfo(value = it, eTLD1 = null)) }.orEmpty(),
    tags = tags.mapNotNull { Tag.of(it) }.toSet(),
    note = notes,
)

internal fun BackupCard.toUpsertCreditCard(vaultId: VaultId): UpsertCreditCard =
    UpsertCreditCard.create(
        vaultId = vaultId,
        name = title,
        cardNumber = number.ifBlank { null },
        expirationDate = expirationString(),
        holder = cardholder,
        cvv = cvv,
        note = notes,
        tags = tags.mapNotNull { Tag.of(it) }.toSet(),
    )

private fun BackupCard.expirationString(): String? {
    val month = expirationMonth?.toInt() ?: return null
    val year = expirationYear?.toInt() ?: return null
    return "%02d/%02d".format(month, year % 100)
}

package de.davis.keygo.core.data.mapper

import de.davis.keygo.core.data.local.model.VaultItem
import de.davis.keygo.core.data.local.model.VaultPassword
import de.davis.keygo.core.data.local.model.Password as DataPassword
import de.davis.keygo.core.domain.model.Password as DomainPassword

fun DomainPassword.toData(vaultId: Long = vaultItemId) = DataPassword(
    id = this.passwordId,
    vaultId = vaultId,
    username = this.username,
    website = this.website,
)

fun DataPassword.asDomain(vaultItem: VaultItem) = DomainPassword(
    vaultItemId = vaultItem.id,
    passwordId = this.id,
    name = vaultItem.name,
    encryptedData = vaultItem.encryptedData,
    username = this.username,
    website = this.website,
    note = vaultItem.shortNote,
)

fun VaultPassword.asDomain() = passwords.map { it.asDomain(vaultItem) }
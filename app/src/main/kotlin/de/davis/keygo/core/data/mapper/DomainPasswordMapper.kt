package de.davis.keygo.core.data.mapper

import de.davis.keygo.generated.item.data.local.entity.PasswordEntity
import de.davis.keygo.generated.item.data.local.entity.VaultItemEntity
import de.davis.keygo.generated.item.data.local.relation.VaultPassword
import de.davis.keygo.core.domain.model.Password as DomainPassword

internal fun DomainPassword.toData(vaultId: Long = vaultItemId) = PasswordEntity(
    passwordId = this.passwordId,
    vaultId = vaultId,
    username = this.username,
    website = this.website,
)

internal fun PasswordEntity.toDomain(vaultItem: VaultItemEntity) = DomainPassword(
    vaultItemId = vaultItem.vaultItemId,
    passwordId = this.passwordId,
    name = vaultItem.name,
    encryptedData = vaultItem.encryptedData,
    username = this.username,
    website = this.website,
    note = vaultItem.note,
)

internal fun VaultPassword.toDomain() = password.toDomain(vaultItemEntity)
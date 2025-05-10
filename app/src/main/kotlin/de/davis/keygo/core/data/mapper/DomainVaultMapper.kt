package de.davis.keygo.core.data.mapper

import de.davis.keygo.core.data.local.model.VaultItemMatch
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.generated.item.data.local.entity.VaultItemEntity
import de.davis.keygo.core.domain.model.VaultItem as DomainVaultItem

internal fun DomainVaultItem.toData() = VaultItemEntity(
    vaultItemId = vaultItemId,
    name = name,
    encryptedData = encryptedData,
    note = note
)

internal fun VaultItemEntity.toDomain() = DomainVaultItem.Basic(
    vaultItemId = vaultItemId,
    name = name,
    note = note,
    encryptedData = encryptedData
)

internal fun VaultItemMatch.toDomain() = VaultSearchResult(
    vaultItemId = item.vaultItemId,
    name = item.name,
    encryptedData = item.encryptedData,
    note = item.note,
    matchedName = matchedName,
    matchedNote = matchedNote
)
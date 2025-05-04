package de.davis.keygo.core.data.mapper

import de.davis.keygo.core.data.local.model.VaultItemMatch
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.core.data.local.model.VaultItem as DataVaultItem
import de.davis.keygo.core.domain.model.VaultItem as DomainVaultItem

fun DomainVaultItem.toData() = DataVaultItem(
    id = vaultItemId,
    name = name,
    encryptedData = encryptedData,
    shortNote = note
)

fun DataVaultItem.toDomain() = DomainVaultItem.Basic(
    vaultItemId = id,
    name = name,
    note = shortNote,
    encryptedData = encryptedData
)

fun VaultItemMatch.toDomain() = VaultSearchResult(
    vaultItemId = item.id,
    name = item.name,
    encryptedData = item.encryptedData,
    note = item.shortNote,
    matchedName = matchedName,
    matchedNote = matchedNote
)
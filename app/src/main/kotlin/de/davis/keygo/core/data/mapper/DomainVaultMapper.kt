package de.davis.keygo.core.data.mapper

import de.davis.keygo.core.data.local.model.VaultItemMatch
import de.davis.keygo.core.domain.model.VaultSearchResult

internal fun VaultItemMatch.toDomain() = VaultSearchResult(
    vaultItemId = item.vaultItemId,
    name = item.name,
    encryptedData = item.encryptedData,
    note = item.note,
    matchedName = matchedName,
    matchedNote = matchedNote
)
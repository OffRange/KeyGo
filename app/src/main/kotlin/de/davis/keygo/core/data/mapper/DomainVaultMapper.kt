package de.davis.keygo.core.data.mapper

import de.davis.keygo.core.data.local.model.VaultItem as DataVaultItem
import de.davis.keygo.core.domain.model.VaultItem as DomainVaultItem

fun DomainVaultItem.toData() = DataVaultItem(
    id = vaultItemId,
    name = name,
    encryptedData = encryptedData,
    shortNote = note
)
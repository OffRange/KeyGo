package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.model.crypto.CryptographicData

interface VaultItem {
    val vaultItemId: Long
    val name: String
    val note: String?
    val encryptedData: CryptographicData
}
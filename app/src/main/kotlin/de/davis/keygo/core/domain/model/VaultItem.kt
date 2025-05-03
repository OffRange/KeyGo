package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.model.crypto.CryptographicData

sealed interface VaultItem {
    val vaultItemId: Long
    val name: String
    val note: String?
    val encryptedData: CryptographicData

    data class Basic(
        override val vaultItemId: Long = 0,
        override val name: String,
        override val encryptedData: CryptographicData,
        override val note: String?
    ) : VaultItem
}
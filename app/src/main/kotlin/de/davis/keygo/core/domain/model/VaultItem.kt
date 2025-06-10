package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.core.domain.`typealias`.ItemId
import de.davis.keygo.processor.annotation.BasicModel
import de.davis.keygo.processor.annotation.Id
import de.davis.keygo.processor.annotation.RootVaultEntity

@RootVaultEntity
sealed interface VaultItem {
    @Id
    val vaultItemId: ItemId
    val name: String
    val note: String?
    val encryptedData: CryptographicData

    @BasicModel
    data class Basic(
        override val vaultItemId: ItemId = 0,
        override val name: String,
        override val encryptedData: CryptographicData,
        override val note: String?
    ) : VaultItem
}
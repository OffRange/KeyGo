package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.processor.annotation.RootVaultEntity

@RootVaultEntity
sealed interface VaultItem : LiteItem {
    override val vaultItemId: Long
    override val name: String
    val encryptedData: SecretData<String>
    val note: String?
}
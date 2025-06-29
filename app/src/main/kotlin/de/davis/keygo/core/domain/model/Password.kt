package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.processor.annotation.Id
import de.davis.keygo.processor.annotation.VaultEntity

@VaultEntity(resString = "password", defaultIconType = "Password")
data class Password(
    @Id
    val passwordId: ItemId = 0,
    val username: String?,
    val website: String?,
    val score: Score,
    override val vaultItemId: ItemId = 0,
    override val name: String,
    override val encryptedData: CryptographicData,
    override val note: String?
) : VaultItem
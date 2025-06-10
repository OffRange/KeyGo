package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.processor.annotation.Ignore

@Ignore
data class VaultSearchResult(
    override val vaultItemId: ItemId,
    override val name: String,
    override val encryptedData: CryptographicData,
    override val note: String?,
    val matchedName: Boolean,
    val matchedNote: Boolean,
) : VaultItem
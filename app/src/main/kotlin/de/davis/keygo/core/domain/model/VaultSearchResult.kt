package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.model.crypto.CryptographicData

data class VaultSearchResult(
    override val vaultItemId: Long,
    override val name: String,
    override val encryptedData: CryptographicData,
    override val note: String?,
    val matchedName: Boolean,
    val matchedNote: Boolean,
) : VaultItem
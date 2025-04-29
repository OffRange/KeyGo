package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.model.crypto.CryptographicData

data class Password(
    val passwordId: Long = 0,
    val username: String?,
    val website: String?,
    override val vaultItemId: Long = 0,
    override val name: String,
    override val encryptedData: CryptographicData,
    override val note: String?
) : VaultItem
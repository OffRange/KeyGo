package de.davis.keygo.core.domain.model

import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.processor.annotation.Id
import de.davis.keygo.processor.annotation.VaultEntity

@VaultEntity(resString = "password", defaultIconType = "Password")
data class Password(
    @Id
    val passwordId: Long = 0,
    val username: String?,
    val website: String?,
    override val vaultItemId: Long = 0,
    override val name: String,
    override val encryptedData: CryptographicData,
    override val note: String?
) : VaultItem
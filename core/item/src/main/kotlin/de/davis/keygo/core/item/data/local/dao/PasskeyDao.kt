package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.davis.keygo.core.item.data.local.entity.PasskeyEntity
import de.davis.keygo.core.item.data.local.pojo.PasskeyMetadataPojo

@Dao
internal interface PasskeyDao {

    @Insert
    suspend fun insertPasskey(passkey: PasskeyEntity)

    @Query("SELECT * FROM PasskeyEntity WHERE credential_id = :credentialId")
    suspend fun getPasskey(credentialId: ByteArray): PasskeyEntity?

    @Query("SELECT EXISTS (SELECT 1 FROM PasskeyEntity WHERE credential_id in (:credentialIds))")
    suspend fun doesCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean

    @Query(
        """
        SELECT passkey.credential_id, passkey.name, passkey.display_name, password.username as password_username, vault.name as vault_name
        FROM PasskeyEntity passkey
        INNER JOIN PasswordEntity password ON passkey.password_id == password.id
        INNER JOIN VaultItemEntity vault ON password.vault_item_id == vault.id
        WHERE passkey.rp = :rpId 
        """
    )
    suspend fun getPasskeysForRP(rpId: String): List<PasskeyMetadataPojo>
}

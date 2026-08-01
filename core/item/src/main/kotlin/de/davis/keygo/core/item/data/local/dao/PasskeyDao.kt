package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.pojo.PasskeyMetadataPojo
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal interface PasskeyDao {

    @Insert
    suspend fun insertPasskey(passkey: PasskeyEntity)

    @Query("SELECT * FROM passkey WHERE credential_id = :credentialId")
    suspend fun getPasskey(credentialId: ByteArray): PasskeyEntity?

    @Query("SELECT * FROM passkey WHERE login_id = :loginId")
    suspend fun getPasskeysForLogin(loginId: ItemId): List<PasskeyEntity>

    @Query("SELECT EXISTS (SELECT 1 FROM passkey WHERE credential_id IN (:credentialIds))")
    suspend fun doesCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean

    @Query(
        """
        SELECT pk.credential_id, pk.name, pk.display_name, p.username AS password_username, i.name AS vault_name
        FROM passkey pk
        INNER JOIN login p ON pk.login_id = p.id
        INNER JOIN item i ON p.id = i.id
        WHERE pk.rp = :rpId
        """
    )
    suspend fun getPasskeysForRP(rpId: String): List<PasskeyMetadataPojo>
}

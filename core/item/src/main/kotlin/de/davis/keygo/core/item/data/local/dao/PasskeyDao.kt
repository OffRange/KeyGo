package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.pojo.PasskeyMetadataPojo
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal abstract class PasskeyDao {

    @Insert
    abstract suspend fun insertPasskey(passkey: PasskeyEntity)

    @Query("SELECT * FROM passkey WHERE credential_id = :credentialId")
    abstract suspend fun getPasskey(credentialId: ByteArray): PasskeyEntity?

    @Query("SELECT * FROM passkey WHERE login_id = :loginId")
    abstract suspend fun getPasskeysForLogin(loginId: ItemId): List<PasskeyEntity>

    @Query("DELETE FROM passkey WHERE login_id = :loginId")
    protected abstract suspend fun deleteAllPasskeysForLogin(loginId: ItemId)

    @Query("DELETE FROM passkey WHERE login_id = :loginId AND rp NOT IN (:rpIds)")
    protected abstract suspend fun deletePasskeysForLoginNotIn(loginId: ItemId, rpIds: Set<String>)

    /**
     * Drops every passkey [loginId] holds for a relying party outside [rpIds].
     *
     * Deletes only. A passkey row carries a credential id and key material, so it can not be
     * reconstructed from a relying party name and this can never put one back.
     */
    @Transaction
    open suspend fun deleteRPsNotIn(loginId: ItemId, rpIds: Set<String>) {
        if (rpIds.isEmpty()) deleteAllPasskeysForLogin(loginId)
        else deletePasskeysForLoginNotIn(loginId, rpIds)
    }

    @Query("SELECT EXISTS (SELECT 1 FROM passkey WHERE credential_id IN (:credentialIds))")
    abstract suspend fun doesCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean

    @Query(
        """
        SELECT pk.credential_id, pk.name, pk.display_name, p.username AS password_username, i.name AS vault_name
        FROM passkey pk
        INNER JOIN login p ON pk.login_id = p.id
        INNER JOIN item i ON p.id = i.id
        WHERE pk.rp = :rpId
        """
    )
    abstract suspend fun getPasskeysForRP(rpId: String): List<PasskeyMetadataPojo>
}

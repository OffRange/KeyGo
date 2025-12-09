package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.davis.keygo.core.item.data.local.entity.PasskeyEntity
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal interface PasskeyDao {

    @Query("SELECT * FROM PasskeyEntity WHERE password_id = :passwordId")
    suspend fun getPasskeysForPassword(passwordId: ItemId): List<PasskeyEntity>

    @Insert
    suspend fun insertPasskey(passkey: PasskeyEntity)

    @Query("SELECT EXISTS (SELECT 1 FROM PasskeyEntity WHERE credential_id in (:credentialIds))")
    suspend fun doesCredentialIdsExist(credentialIds: Set<ByteArray>): Boolean
}
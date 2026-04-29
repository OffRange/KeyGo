package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.davis.keygo.core.item.data.local.entity.KeyInformation
import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.data.local.pojo.VaultMetadata
import de.davis.keygo.core.item.data.local.pojo.VaultUpdater
import de.davis.keygo.core.item.domain.alias.VaultId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vault: VaultEntity)

    @Update(entity = VaultEntity::class)
    suspend fun update(vaultUpdater: VaultUpdater)

    @Query("DELETE FROM vault WHERE id = :id")
    suspend fun delete(id: VaultId)

    @Query("SELECT wrappedKey, keyNonce FROM vault WHERE id = :id")
    suspend fun getKeyInfoById(id: VaultId): KeyInformation?

    @Query(
        """
        SELECT vault.id as vaultId, vault.name, vault.icon, COUNT(item.id) as count
        FROM vault
        LEFT JOIN item ON vault.id = item.vault_id
        GROUP BY vault.id
        """
    )
    fun observeAllVaultMetadata(): Flow<List<VaultMetadata>>
}

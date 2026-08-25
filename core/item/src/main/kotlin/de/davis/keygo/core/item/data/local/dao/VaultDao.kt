package de.davis.keygo.core.item.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
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

    @Query("SELECT wrapped_key, key_nonce FROM vault WHERE id = :id")
    suspend fun getKeyInfoById(id: VaultId): KeyInformation?

    @Query(
        """
        SELECT vault.id as vaultId, vault.name, vault.icon, vault.created_at as createdAt, COUNT(item.id) as count
        FROM vault
        LEFT JOIN item ON vault.id = item.vault_id
        GROUP BY vault.id
        """
    )
    fun observeAllVaultMetadata(): Flow<List<VaultMetadata>>

    @Query(
        """
        SELECT vault.id as vaultId, vault.name, vault.icon, vault.created_at as createdAt, COUNT(item.id) as count
        FROM vault
        LEFT JOIN item ON vault.id = item.vault_id
        WHERE vault.id = :id
        GROUP BY vault.id
        """
    )
    suspend fun getVaultMetadata(id: VaultId): VaultMetadata?

    @Query("SELECT id FROM vault WHERE id != :exclude ORDER BY created_at DESC LIMIT 1")
    suspend fun lastCreatedVaultId(exclude: VaultId): VaultId?
}

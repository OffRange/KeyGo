package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.VaultItemEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItem
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItemSearchResult
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface VaultDao {

    @Upsert
    suspend fun upsert(vaultItem: VaultItemEntity): ItemId

    @Query("DELETE FROM VaultItemEntity WHERE id = :id")
    suspend fun delete(id: ItemId)

    @Query("SELECT EXISTS(SELECT 1 FROM VaultItemEntity WHERE name = :name AND (:excludeId IS NULL OR id != :excludeId))")
    suspend fun existsName(name: String, excludeId: ItemId? = null): Boolean

    @Query("SELECT * FROM VaultItemEntity WHERE id = :id")
    suspend fun getVaultItemById(id: ItemId): VaultItemEntity?

    @Query(
        """
        SELECT v.id, v.name, (name LIKE '%' || :query || '%') AS matchedName, (note LIKE '%' || :query || '%') AS matchedNote
        FROM VaultItemEntity v
        WHERE name LIKE '%' || :query || '%' OR COALESCE(note, '') LIKE '%' || :query || '%'
        """
    )
    suspend fun searchVaultItem(query: String): List<LightweightVaultItemSearchResult>


    @Query("SELECT v.id, v.name FROM VaultItemEntity v")
    fun observeLiteVaultItems(): Flow<List<LightweightVaultItem>>
}
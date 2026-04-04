package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.VaultItemEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItem
import de.davis.keygo.core.item.data.local.pojo.LightweightVaultItemSearchResult
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface VaultDao {

    @Upsert
    suspend fun upsert(vaultItem: VaultItemEntity): ItemId

    @Query("DELETE FROM VaultItemEntity WHERE id = :id")
    suspend fun delete(id: ItemId)

    @Query("SELECT name FROM VaultItemEntity WHERE id = :itemId")
    suspend fun getNameById(itemId: ItemId): String?

    @Query("SELECT EXISTS(SELECT 1 FROM VaultItemEntity WHERE name = :name AND (:excludeId IS NULL OR id != :excludeId))")
    suspend fun existsName(name: String, excludeId: ItemId? = null): Boolean

    @Query("SELECT * FROM VaultItemEntity WHERE id = :id")
    suspend fun getVaultItemById(id: ItemId): VaultItemEntity?

    @Query(
        """
        SELECT v.id, v.name, v.itemType, v.pinned, (name LIKE '%' || :query || '%') AS matchedName, (note LIKE '%' || :query || '%') AS matchedNote
        FROM VaultItemEntity v
        WHERE (:itemType IS NULL OR itemType = :itemType)
          AND (name LIKE '%' || :query || '%' OR COALESCE(note, '') LIKE '%' || :query || '%')
        """
    )
    suspend fun searchVaultItem(
        query: String,
        itemType: VaultItemType? = null
    ): List<LightweightVaultItemSearchResult>

    @Query("UPDATE VaultItemEntity SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: ItemId, pinned: Boolean)

    @Query("SELECT v.id, v.name, v.itemType, v.pinned FROM VaultItemEntity v")
    fun observeLiteVaultItems(): Flow<List<LightweightVaultItem>>
}

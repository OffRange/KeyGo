package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightItem
import de.davis.keygo.core.item.data.local.pojo.LightweightItemSearchResult
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ItemDao {

    @Upsert
    suspend fun upsert(item: ItemEntity): Long

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun delete(id: ItemId)

    @Query("SELECT name FROM item WHERE id = :id")
    suspend fun getNameById(id: ItemId): String?

    @Query("SELECT EXISTS(SELECT 1 FROM item WHERE name = :name AND (:excludeId IS NULL OR id != :excludeId))")
    suspend fun existsName(name: String, excludeId: ItemId? = null): Boolean

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun getItemById(id: ItemId): ItemEntity?

    @Query(
        """
        SELECT i.id, i.name, i.itemType, i.pinned,
               (name LIKE '%' || :query || '%') AS matchedName,
               (note LIKE '%' || :query || '%') AS matchedNote
        FROM item i
        WHERE (:itemType IS NULL OR itemType = :itemType)
          AND (name LIKE '%' || :query || '%' OR COALESCE(note, '') LIKE '%' || :query || '%')
        """
    )
    suspend fun searchItem(
        query: String,
        itemType: VaultItemType? = null,
    ): List<LightweightItemSearchResult>

    @Query("UPDATE item SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: ItemId, pinned: Boolean)

    @Query("SELECT i.id, i.name, i.itemType, i.pinned FROM item i")
    fun observeLiteItems(): Flow<List<LightweightItem>>
}

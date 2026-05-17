package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import de.davis.keygo.core.item.data.local.entity.TagCrossRef
import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIgnore(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCrossRef(ref: TagCrossRef)

    @Query("DELETE FROM tag_cross_ref WHERE item_id = :itemId")
    protected abstract suspend fun clearCrossRefsForItem(itemId: ItemId)

    @Query("SELECT id FROM tag WHERE normalized = :normalized")
    internal abstract suspend fun findIdByNormalized(normalized: String): Long?

    @Query("SELECT * FROM tag WHERE EXISTS (SELECT 1 FROM tag_cross_ref WHERE tag_id = tag.id)")
    abstract fun observeAllTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT DISTINCT x.item_id FROM tag_cross_ref x
        JOIN tag t ON t.id = x.tag_id
        WHERE t.value IN (:values)
        """
    )
    abstract fun observeItemIdsWithAnyTag(values: Set<String>): Flow<List<ItemId>>

    @Query("SELECT tag_id FROM tag_cross_ref WHERE item_id = :itemId")
    abstract suspend fun tagIdsForItem(itemId: ItemId): List<Long>

    @Query(
        "DELETE FROM tag WHERE id IN (:tagIds) " +
                "AND NOT EXISTS (SELECT 1 FROM tag_cross_ref WHERE tag_id = tag.id)"
    )
    abstract suspend fun pruneOrphans(tagIds: List<Long>)

    @Transaction
    open suspend fun syncTags(itemId: ItemId, tags: Set<TagEntity>) {
        val previousTagIds = tagIdsForItem(itemId)
        clearCrossRefsForItem(itemId)
        for (tag in tags) {
            insertIgnore(tag)
            val tagId = findIdByNormalized(tag.normalized) ?: continue
            insertCrossRef(TagCrossRef(itemId = itemId, tagId = tagId))
        }
        if (previousTagIds.isNotEmpty()) pruneOrphans(previousTagIds)
    }
}

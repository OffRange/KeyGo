package de.davis.passwordmanager.database.daos

import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import de.davis.passwordmanager.database.entities.SecureElementEntity
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.junction.SecureElementTagCrossRef
import de.davis.passwordmanager.database.entities.wrappers.CombinedElement
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
abstract class SecureElementWithTagDao {

    @Transaction
    @Query("SELECT * FROM SecureElement WHERE (:typeId IS NULL OR type = :typeId)")
    abstract suspend fun getCombinedElement(typeId: Int? = null): List<CombinedElement>

    @Transaction
    @Query("SELECT * FROM SecureElement WHERE (:typeId IS NULL OR type = :typeId)")
    abstract fun getCombinedElementFlow(typeId: Int? = null): Flow<List<CombinedElement>>

    @Transaction
    @Query("SELECT * FROM SecureElement WHERE title LIKE :query")
    abstract suspend fun getByTitle(query: String): List<CombinedElement>

    @Transaction
    @VisibleForTesting
    @Query("SELECT * FROM SecureElement WHERE id = :id")
    abstract suspend fun getCombinedElementById(id: Long): CombinedElement

    @Insert
    protected abstract suspend fun insert(secureElementEntity: SecureElementEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(tags: List<Tag>): List<Long>

    @Insert
    protected abstract suspend fun insert(crossRef: SecureElementTagCrossRef)

    @Delete
    abstract suspend fun delete(secureElementEntity: SecureElementEntity)

    @Delete
    abstract suspend fun delete(tag: Tag)

    @Delete
    abstract suspend fun delete(crossRef: SecureElementTagCrossRef)

    @Update
    abstract suspend fun update(secureElementEntity: SecureElementEntity)

    @Query("SELECT tagId FROM TAG WHERE name = :name")
    protected abstract suspend fun getIdByTagName(name: String): Long

    @Query("DELETE FROM SecureElementTagCrossRef WHERE id = :elementId")
    protected abstract suspend fun deleteTagRelationsTo(elementId: Long)

    suspend fun insert(elementWithTags: CombinedElement): Long {
        val elementId = insert(elementWithTags.secureElementEntity)
        insertTags(elementWithTags.tags, elementId)
        return elementId
    }

    private suspend fun insertTags(tags: List<Tag>, elementId: Long) {
        val tagIds = insert(tags)

        tagIds.forEachIndexed { index, l ->
            insert(
                SecureElementTagCrossRef(
                    elementId,
                    if (l > 0) l else getIdByTagName(tags[index].name)
                )
            )
        }
    }

    suspend fun update(elementWithTags: CombinedElement) {
        elementWithTags.secureElementEntity.run {
            timestamps.modifiedAt = Date()
            update(this)
            id.run {
                deleteTagRelationsTo(this)
                insertTags(elementWithTags.tags, this)
            }
        }
    }

    @Transaction
    @Query("SELECT * FROM SecureElement WHERE favorite ORDER BY ROWID ASC LIMIT :limit")
    abstract suspend fun getFavorites(limit: Int = 5): List<CombinedElement>

    @Transaction
    @Query("SELECT * FROM SecureElement WHERE created_at ORDER BY ROWID DESC LIMIT :limit")
    abstract suspend fun getLastCreated(limit: Int = 5): List<CombinedElement>

    @Transaction
    @Query("SELECT * FROM SecureElement WHERE modified_at IS NOT NULL ORDER BY modified_at DESC LIMIT :limit")
    abstract suspend fun getLastModified(limit: Int = 5): List<CombinedElement>
}
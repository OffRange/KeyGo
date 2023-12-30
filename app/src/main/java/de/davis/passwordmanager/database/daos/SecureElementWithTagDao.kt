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
import de.davis.passwordmanager.database.entities.TagWithCountEntity
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
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Query("SELECT * FROM SecureElement WHERE id = :id")
    abstract suspend fun getCombinedElementById(id: Long): CombinedElement

    @Query("SELECT * FROM Tag")
    abstract suspend fun getTags(): List<Tag>

    @Query("SELECT Tag.*, COUNT(SecureElementTagCrossRef.tagId) AS count FROM Tag LEFT JOIN SecureElementTagCrossRef ON Tag.tagId = SecureElementTagCrossRef.tagId GROUP BY Tag.tagId")
    abstract fun getTagsWithCount(): Flow<List<TagWithCountEntity>>

    @Insert
    protected abstract suspend fun insert(secureElementEntity: SecureElementEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(crossRef: SecureElementTagCrossRef)

    @Delete
    protected abstract suspend fun deleteElementsAbs(secureElementEntities: List<SecureElementEntity>)

    @Transaction
    open suspend fun deleteElements(secureElementEntities: List<SecureElementEntity>) {
        deleteElementsAbs(secureElementEntities)
        deleteUnusedTags()
    }

    @Delete
    abstract suspend fun deleteTags(tags: List<Tag>)

    @Update
    abstract suspend fun update(secureElementEntity: SecureElementEntity)

    @Query("SELECT tagId FROM TAG WHERE name = :name LIMIT 1")
    protected abstract suspend fun getIdByTagName(name: String): Long

    @Query("DELETE FROM SecureElementTagCrossRef WHERE id = :elementId")
    protected abstract suspend fun deleteTagRelationsTo(elementId: Long)

    @Query("DELETE FROM Tag  WHERE tagId NOT IN (SELECT tagId FROM SecureElementTagCrossRef)")
    protected abstract suspend fun deleteUnusedTags()

    @Transaction
    open suspend fun insert(elementWithTags: CombinedElement): Long {
        val elementId = insert(elementWithTags.secureElementEntity)
        insertTagsAndMakeRelation(elementWithTags.tags, elementId)
        return elementId
    }

    private suspend fun insertTagsAndMakeRelation(tags: List<Tag>, elementId: Long) {
        tags.forEach {
            var tagId = getIdByTagName(it.name)

            if (tagId == 0L) {
                tagId = insert(it)
            }

            insert(
                SecureElementTagCrossRef(
                    elementId,
                    tagId
                )
            )
        }
    }

    @Update(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun updateTag(tag: Tag): Int

    @Transaction
    open suspend fun update(elementWithTags: CombinedElement) {
        elementWithTags.secureElementEntity.run {
            timestamps.modifiedAt = Date()
            update(this)
            id.run {
                deleteTagRelationsTo(this)
                insertTagsAndMakeRelation(elementWithTags.tags, this)
                deleteUnusedTags()
            }
        }
    }

    suspend fun updateModifiedAt(elementWithTags: CombinedElement) {
        elementWithTags.secureElementEntity.run {
            timestamps.modifiedAt = Date()
            update(this)
        }
    }

    @Query(
        """
        INSERT OR IGNORE INTO SecureElementTagCrossRef (id, tagId)
        SELECT DISTINCT sec.id, (SELECT tagId FROM Tag WHERE name = :newTagName) 
        FROM SecureElementTagCrossRef sec
        INNER JOIN Tag t ON sec.tagId = t.tagId
        WHERE t.name IN (:tagsToDelete)
    """
    )
    protected abstract suspend fun reassignSecureElementsToNewTag(
        newTagName: String,
        tagsToDelete: List<String>
    )

    @Transaction
    open suspend fun mergeTags(tags: List<Tag>, resultingTagName: String) {
        if (tags.any { it.tagId == 0L })
            throw IllegalStateException("Tags must be reference to database entries")

        val tagId =
            tags.find { it.name == resultingTagName }?.tagId ?: getIdByTagName(resultingTagName)


        if (tagId == 0L)
            insert(Tag(resultingTagName))


        val cleanedTags = tags.filter { it.name != resultingTagName }
        reassignSecureElementsToNewTag(resultingTagName, cleanedTags.map { it.name })

        deleteTags(cleanedTags)
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
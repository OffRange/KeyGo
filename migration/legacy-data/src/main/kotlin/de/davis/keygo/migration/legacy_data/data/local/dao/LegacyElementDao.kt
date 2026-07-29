package de.davis.keygo.migration.legacy_data.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity
import de.davis.keygo.migration.legacy_data.data.local.pojo.LegacyElementWithTags

@Dao
internal interface LegacyElementDao {

    @Transaction
    @Query("SELECT * FROM SecureElement")
    suspend fun getAllWithTags(): List<LegacyElementWithTags>

    @Query("SELECT COUNT(*) FROM SecureElement")
    suspend fun count(): Int

    @Query("DELETE FROM SecureElement WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    // Insert methods exist for tests only. Production code never writes elements or tags; it
    // reads them and deletes the ones it has imported.
    @Insert
    suspend fun insertElement(element: LegacySecureElementEntity): Long

    @Insert
    suspend fun insertTag(tag: LegacyTagEntity): Long

    @Insert
    suspend fun insertCrossRef(crossRef: LegacySecureElementTagCrossRef)
}

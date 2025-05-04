package de.davis.keygo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.davis.keygo.core.data.local.model.VaultItem
import de.davis.keygo.core.data.local.model.VaultItemMatch
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert
    suspend fun insert(vaultItem: VaultItem): Long

    @Query("DELETE FROM VaultItem WHERE id = :vaultItemId")
    suspend fun delete(vaultItemId: Long)

    @Query("SELECT * FROM VaultItem WHERE id = :id")
    suspend fun getVaultItemById(id: Long): VaultItem?

    @Query("SELECT *, (name LIKE '%' || :query || '%') AS matchedName, (short_note LIKE '%' || :query || '%') AS matchedNote FROM VaultItem WHERE name LIKE '%' || :query || '%' OR COALESCE(short_note, '') LIKE '%' || :query || '%'")
    suspend fun searchVaultItem(query: String): List<VaultItemMatch>

    @Query("SELECT * FROM VaultItem")
    fun getAllVaultItems(): Flow<List<VaultItem>>
}
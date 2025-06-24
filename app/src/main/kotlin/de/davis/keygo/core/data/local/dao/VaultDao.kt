package de.davis.keygo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.davis.keygo.core.data.local.model.VaultItemMatch
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.generated.item.data.local.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface VaultDao {

    @Upsert
    suspend fun upsert(vaultItem: VaultItemEntity): ItemId

    @Query("DELETE FROM VaultItemEntity WHERE vault_item_id = :vaultItemId")
    suspend fun delete(vaultItemId: ItemId)

    @Query("SELECT * FROM VaultItemEntity WHERE vault_item_id = :id")
    suspend fun getVaultItemById(id: ItemId): VaultItemEntity?

    @Query("SELECT *, (name LIKE '%' || :query || '%') AS matchedName, (note LIKE '%' || :query || '%') AS matchedNote FROM VaultItemEntity WHERE name LIKE '%' || :query || '%' OR COALESCE(note, '') LIKE '%' || :query || '%'")
    suspend fun searchVaultItem(query: String): List<VaultItemMatch>

    @Query("SELECT * FROM VaultItemEntity")
    fun getAllVaultItems(): Flow<List<VaultItemEntity>>
}
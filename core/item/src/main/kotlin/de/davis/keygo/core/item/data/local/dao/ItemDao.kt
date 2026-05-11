package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.pojo.ItemWrappedKeyRecord
import de.davis.keygo.core.item.data.local.pojo.LightweightItem
import de.davis.keygo.core.item.data.local.pojo.LightweightItemSearchResult
import de.davis.keygo.core.item.data.local.pojo.MovableItemPojo
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
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

    @Query("SELECT EXISTS(SELECT 1 FROM item WHERE name = :name AND (:excludeId IS NULL OR id != :excludeId) AND (:vaultId IS NULL OR vault_id = :vaultId))")
    suspend fun existsName(
        name: String,
        excludeId: ItemId? = null,
        vaultId: VaultId? = null
    ): Boolean

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun getItemById(id: ItemId): ItemEntity?

    @Query(
        """
        SELECT i.id, i.name, i.item_type AS itemType, i.pinned,
               (name LIKE '%' || :query || '%') AS matchedName,
               (note LIKE '%' || :query || '%') AS matchedNote
        FROM item i
        WHERE (:itemType IS NULL OR item_type = :itemType)
          AND (name LIKE '%' || :query || '%' OR COALESCE(note, '') LIKE '%' || :query || '%')
        """
    )
    suspend fun searchItem(
        query: String,
        itemType: VaultItemType? = null,
    ): List<LightweightItemSearchResult>

    @Query("UPDATE item SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: ItemId, pinned: Boolean)

    @Query("SELECT i.id, i.name, i.item_type as itemType, i.pinned FROM item i WHERE (:vaultId IS NULL OR vault_id = :vaultId)")
    fun observeLiteItems(vaultId: VaultId? = null): Flow<List<LightweightItem>>

    @Query(
        """
        SELECT i.id AS itemId,
            i.wrapped_key as item_wrapped_key,
            i.key_nonce AS item_key_nonce,
            v.id AS vaultID,
            v.wrapped_key AS vault_wrapped_key,
            v.key_nonce AS vault_key_nonce
        FROM item i
        INNER JOIN vault v ON i.vault_id = v.id
        WHERE i.id = :itemId
        """
    )
    suspend fun getItemKeyRecord(itemId: ItemId): ItemWrappedKeyRecord?

    @Query("SELECT id, wrapped_key, key_nonce FROM item WHERE vault_id = :vaultId")
    suspend fun getMovableItemsByVault(vaultId: VaultId): List<MovableItemPojo>

    @Query(
        "UPDATE item SET vault_id = :newVaultId, wrapped_key = :wrappedKey, key_nonce = :keyNonce " +
                "WHERE id = :id"
    )
    suspend fun moveItem(
        id: ItemId,
        newVaultId: VaultId,
        wrappedKey: ByteArray,
        keyNonce: ByteArray,
    )

    @Transaction
    suspend fun moveItemsToVault(items: List<MovableItemPojo>, newVaultId: VaultId) {
        items.forEach {
            moveItem(
                id = it.id,
                newVaultId = newVaultId,
                wrappedKey = it.keyInformation.wrappedKey,
                keyNonce = it.keyInformation.keyNonce,
            )
        }
    }
}

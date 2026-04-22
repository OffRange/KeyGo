package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import de.davis.keygo.core.item.data.local.entity.KeyInformation
import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.data.local.pojo.ActiveVaultKeyInformation
import de.davis.keygo.core.item.data.local.pojo.VaultMetadata
import de.davis.keygo.core.item.domain.alias.VaultId

@Dao
internal interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vault: VaultEntity)

    @Deprecated(
        message = "Use deleteVault() instead. Raw deletion bypasses active vault safety checks and can leave the app without an active vault.",
        replaceWith = ReplaceWith("deleteVault(id)"),
        level = DeprecationLevel.ERROR
    )
    @Query("DELETE FROM vault WHERE id = :id")
    suspend fun delete(id: VaultId)

    @Query("SELECT wrappedKey, keyNonce FROM vault WHERE id = :id")
    suspend fun getKeyInfoById(id: VaultId): KeyInformation?


    @Query("SELECT wrappedKey, keyNonce, vault.id as vaultId FROM vault INNER JOIN active_vault ON vault.id = active_vault.vaultId WHERE active_vault.id = 1")
    suspend fun getActiveKeyInfo(): ActiveVaultKeyInformation

    @Query("SELECT vaultId FROM active_vault WHERE id = 1")
    suspend fun getActiveItemId(): VaultId

    @Query("SELECT id FROM vault WHERE id != :excludingId LIMIT 1")
    suspend fun anyOtherItemId(excludingId: VaultId): VaultId?

    @Query("INSERT OR REPLACE INTO active_vault (id, vaultId) VALUES (1, :newId)")
    suspend fun setActive(newId: VaultId)

    @Suppress("DEPRECATION_ERROR")
    @Transaction
    suspend fun deleteVault(id: VaultId): Boolean {
        if (id == getActiveItemId()) {
            val replacement = anyOtherItemId(excludingId = id)
                ?: return false //Cannot delete the last remaining item
            setActive(replacement)
        }
        delete(id)
        return true
    }

    @Query("SELECT id as vaultId, name, icon FROM vault")
    suspend fun getAllVaultMetadata(): List<VaultMetadata>
}

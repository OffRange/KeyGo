package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import de.davis.keygo.core.item.domain.alias.VaultId

@Dao
internal interface ActiveVaultDao {

    @Query("SELECT vaultId FROM active_vault WHERE id = 1")
    suspend fun getActiveItemId(): VaultId

    @Query("SELECT id FROM vault WHERE id != :excludingId LIMIT 1")
    suspend fun anyOtherItemId(excludingId: VaultId): VaultId?

    @Query("UPDATE active_vault SET vaultId = :newId WHERE id = 0")
    suspend fun setActive(newId: VaultId)

    @Query("DELETE FROM vault WHERE id = :id")
    suspend fun delete(id: VaultId)

    @Transaction
    suspend fun deleteVault(id: VaultId) {
        if (id == getActiveItemId()) {
            val replacement = anyOtherItemId(excludingId = id)
                ?: error("Cannot delete the last remaining item")
            setActive(replacement)
        }
        delete(id)
    }
}
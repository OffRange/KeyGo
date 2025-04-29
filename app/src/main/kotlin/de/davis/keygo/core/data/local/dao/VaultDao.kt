package de.davis.keygo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.davis.keygo.core.data.local.model.VaultItem

@Dao
interface VaultDao {

    @Insert
    suspend fun insert(vaultItem: VaultItem): Long

    @Query("SELECT * FROM VaultItem WHERE id = :id")
    suspend fun getVaultItemById(id: Long): VaultItem?
}
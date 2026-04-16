package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.domain.alias.VaultId

@Dao
internal interface VaultDao {

    @Upsert
    suspend fun upsert(vault: VaultEntity)

    @Query("DELETE FROM vault WHERE id = :id")
    suspend fun delete(id: VaultId)

    @Query("SELECT * FROM vault WHERE id = :id")
    suspend fun getById(id: VaultId): VaultEntity?
}

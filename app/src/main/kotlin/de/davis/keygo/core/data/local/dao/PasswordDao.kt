package de.davis.keygo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.generated.item.data.local.entity.PasswordEntity
import de.davis.keygo.generated.item.data.local.relation.VaultPassword
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasswordDao {

    @Transaction
    @Query("SELECT * FROM VaultItemEntity")
    fun getVaultPasswords(): Flow<List<VaultPassword>>


    @Transaction
    @Query("SELECT * FROM VaultItemEntity WHERE vault_item_id = :vaultId")
    fun observeVaultPassword(vaultId: ItemId): Flow<VaultPassword>

    @Insert
    suspend fun insert(password: PasswordEntity): ItemId
}
package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasswordDao {

    @Transaction
    @Query("SELECT * FROM PasswordEntity")
    fun getAllPasswords(): Flow<List<VaultPassword>>


    @Transaction
    @Query("SELECT * FROM PasswordEntity WHERE vault_item_id = :vaultId")
    fun observeVaultPassword(vaultId: ItemId): Flow<VaultPassword>

    @Transaction
    @Query(
        """
        SELECT vault.id vault_item_id, vault.name name, password.id password_id, password.username username
        FROM VaultItemEntity vault
        JOIN PasswordEntity password ON vault.id = password.vault_item_id
        WHERE EXISTS (
            SELECT 1 FROM DomainInfoEntity domain
            WHERE domain.password_id = password.id
            AND domain.eTLD1 in (:etld1s) COLLATE NOCASE
        )
        LIMIT :limit
        """
    )
    suspend fun getByTLDs(etld1s: Set<String>, limit: Int): List<LightweightPassword>

    @Transaction
    @Query("SELECT * FROM PasswordEntity WHERE vault_item_id = :vaultId")
    suspend fun getVaultPassword(vaultId: ItemId): VaultPassword?

    @Upsert
    suspend fun upsert(password: PasswordEntity): ItemId
}
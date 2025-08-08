package de.davis.keygo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    /**
     * Finds all [VaultPassword] pairs where the username or website
     * contains the given substrings (case-insensitive).
     */
    // TODO: provide a lighter entity for search results
    //  Item (this)                          - only requires name, username, website, id
    //  Autofill (findVaultPasswordsByUrl)   - only requires name, username, id
    //  handle lowercase - maybe use a separate lowercase column?
    @Query("SELECT * FROM VaultItemEntity WHERE vault_item_id IN (SELECT vault_item_id FROM PasswordEntity WHERE username LIKE '%' || :username || '%' OR website LIKE '%' || :website || '%')")
    suspend fun searchVaultPassword(username: String?, website: String?): List<VaultPassword>

    /**
     * Retrieves all [VaultPassword]s where the stored website
     * appears anywhere inside the provided URL (case-insensitive).
     */
    @Transaction
    @Query("SELECT * FROM VaultItemEntity AS vault JOIN PasswordEntity AS pass ON vault.vault_item_id == pass.vault_item_id WHERE :url LIKE '%' || website || '%' COLLATE NOCASE")
    suspend fun findVaultPasswordsByUrl(url: String): List<VaultPassword>

    @Transaction
    @Query("SELECT * FROM VaultItemEntity WHERE vault_item_id = :vaultId")
    suspend fun getVaultPassword(vaultId: ItemId): VaultPassword?

    @Upsert
    suspend fun upsert(password: PasswordEntity): ItemId
}
package de.davis.keygo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.davis.keygo.core.data.local.model.Password
import de.davis.keygo.core.data.local.model.VaultPassword
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {

    @Transaction
    @Query("SELECT * FROM VaultItem")
    fun getVaultPasswords(): Flow<List<VaultPassword>>


    @Transaction
    @Query("SELECT * FROM VaultItem WHERE id = :vaultId")
    suspend fun getVaultPasswords(vaultId: Long): VaultPassword

    @Insert
    suspend fun insert(password: Password): Long
}
package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.PasswordScoreEntry
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasswordDao {

    @Transaction
    @Query("SELECT * FROM password")
    fun getAllPasswords(): Flow<List<VaultPassword>>

    @Transaction
    @Query("SELECT * FROM password WHERE id = :id")
    fun observeVaultPassword(id: ItemId): Flow<VaultPassword?>

    @Transaction
    @Query(
        """
        SELECT i.id, i.name, i.pinned, p.username
        FROM item i
        JOIN password p ON i.id = p.id
        WHERE (NOT :requireTotp OR p.totp_secret IS NOT NULL)
        AND EXISTS (
            SELECT 1 FROM domain_info d
            WHERE d.password_id = p.id
            AND d.eTLD1 IN (:etld1s) COLLATE NOCASE
        )
        LIMIT :limit
        """
    )
    suspend fun getByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        limit: Int
    ): List<LightweightPassword>

    @Transaction
    @Query("SELECT * FROM password WHERE id = :id")
    suspend fun getVaultPassword(id: ItemId): VaultPassword?

    @Query("SELECT id, score FROM password")
    fun observePasswordScores(): Flow<List<PasswordScoreEntry>>

    @Upsert
    suspend fun upsert(password: PasswordEntity)
}

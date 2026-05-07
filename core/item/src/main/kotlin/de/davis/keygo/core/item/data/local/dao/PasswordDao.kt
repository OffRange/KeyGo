package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.data.local.pojo.PasswordScoreProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface PasswordDao {

    @Transaction
    @Query("SELECT * FROM login")
    fun getAllPasswords(): Flow<List<LoginProjection>>

    @Transaction
    @Query("SELECT * FROM login WHERE id = :id")
    fun observeVaultPassword(id: ItemId): Flow<LoginProjection?>

    @Transaction
    @Query(
        """
        SELECT i.id, i.name, i.pinned, l.username
        FROM item i
        JOIN login l ON i.id = l.id
        WHERE (
            NOT :requireTotp
            OR EXISTS (
                SELECT 1
                FROM totp t
                WHERE t.login_id = l.id
            )
        )
        AND EXISTS (
            SELECT 1
            FROM domain_info d
            WHERE d.login_id = l.id
            AND d.eTLD1 IN (:etld1s) COLLATE NOCASE
        )
        LIMIT :limit
        """
    )
    suspend fun getByTLDs(
        etld1s: Set<String>,
        requireTotp: Boolean,
        limit: Int
    ): List<LightweightLogin>

    @Transaction
    @Query("SELECT * FROM login WHERE id = :id")
    suspend fun getVaultPassword(id: ItemId): LoginProjection?

    @Transaction
    @Query("SELECT * FROM login WHERE id IN (SELECT id FROM item WHERE vault_id = :vaultId)")
    suspend fun getPasswordsByVault(vaultId: VaultId): List<LoginProjection>

    @Query("SELECT login_id AS id, passwordScore FROM password")
    fun observePasswordScores(): Flow<List<PasswordScoreProjection>>

    @Upsert
    suspend fun upsert(password: PasswordEntity)
}

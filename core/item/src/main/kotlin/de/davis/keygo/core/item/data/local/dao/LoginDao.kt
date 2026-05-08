package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightLogin
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface LoginDao {

    @Upsert
    suspend fun upsert(login: LoginEntity)

    @Transaction
    @Query("SELECT * FROM login")
    fun observeAll(): Flow<List<LoginProjection>>

    @Transaction
    @Query("SELECT * FROM login WHERE id = :id")
    fun observeById(id: ItemId): Flow<LoginProjection?>

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
        limit: Int,
    ): List<LightweightLogin>

    @Transaction
    @Query("SELECT * FROM login WHERE id = :id")
    suspend fun getById(id: ItemId): LoginProjection?

    @Transaction
    @Query("SELECT * FROM login WHERE id IN (SELECT id FROM item WHERE vault_id = :vaultId)")
    suspend fun getByVault(vaultId: VaultId): List<LoginProjection>
}

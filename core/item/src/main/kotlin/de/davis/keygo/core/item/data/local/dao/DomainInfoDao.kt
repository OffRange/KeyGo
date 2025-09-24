package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity

@Dao
internal abstract class DomainInfoDao {

    @Query("DELETE FROM DomainInfoEntity WHERE password_id = :passwordId AND (value IS NULL OR  value NOT IN (:except))")
    protected abstract suspend fun deleteAllDomainsForPassword(
        passwordId: Long,
        except: Set<String> = emptySet()
    )

    @Upsert
    protected abstract suspend fun upsertAll(domains: Set<DomainInfoEntity>): List<Long>

    @Transaction
    open suspend fun syncForPassword(passwordId: Long, domains: Set<DomainInfoEntity>) {
        if (domains.isEmpty()) {
            deleteAllDomainsForPassword(passwordId)
            return
        }

        val adjusted = domains.map { it.copy(passwordId = passwordId) }.toSet()
        upsertAll(adjusted)
        deleteAllDomainsForPassword(passwordId, adjusted.map { it.value }.toSet())
    }
}
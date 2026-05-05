package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal abstract class DomainInfoDao {

    @Query("DELETE FROM domain_info WHERE password_id = :passwordId AND (value IS NULL OR value NOT IN (:except))")
    protected abstract suspend fun deleteAllDomainsForPassword(
        passwordId: ItemId,
        except: Set<String> = emptySet()
    )

    @Upsert
    abstract suspend fun upsertAll(domains: Set<DomainInfoEntity>)

    @Transaction
    open suspend fun syncForPassword(passwordId: ItemId, domains: Set<DomainInfoEntity>) {
        if (domains.isEmpty()) {
            deleteAllDomainsForPassword(passwordId)
            return
        }

        val adjusted = domains.map { it.copy(passwordId = passwordId) }.toSet()
        upsertAll(adjusted)
        deleteAllDomainsForPassword(passwordId, adjusted.map { it.value }.toSet())
    }
}

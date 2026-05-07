package de.davis.keygo.core.item.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal abstract class DomainInfoDao {

    @Query("DELETE FROM domain_info WHERE login_id = :loginId AND (value IS NULL OR value NOT IN (:except))")
    protected abstract suspend fun deleteAllDomainsForPassword(
        loginId: ItemId,
        except: Set<String> = emptySet()
    )

    @Upsert
    abstract suspend fun upsertAll(domains: Set<DomainInfoEntity>)

    @Transaction
    open suspend fun syncForPassword(loginId: ItemId, domains: Set<DomainInfoEntity>) {
        if (domains.isEmpty()) {
            deleteAllDomainsForPassword(loginId)
            return
        }

        val adjusted = domains.map { it.copy(loginId = loginId) }.toSet()
        upsertAll(adjusted)
        deleteAllDomainsForPassword(loginId, adjusted.map { it.value }.toSet())
    }
}

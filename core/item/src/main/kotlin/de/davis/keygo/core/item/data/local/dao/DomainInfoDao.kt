package de.davis.keygo.core.item.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.domain.alias.ItemId

@Dao
internal abstract class DomainInfoDao {

    @Query("DELETE FROM domain_info WHERE login_id = :loginId AND (value IS NULL OR value NOT IN (:except))")
    protected abstract suspend fun deleteAllDomainsForLogin(
        loginId: ItemId,
        except: Set<String> = emptySet(),
    )

    @Upsert
    abstract suspend fun upsertAll(domains: Set<DomainInfoEntity>)

    @Transaction
    open suspend fun syncForLogin(loginId: ItemId, domains: Set<DomainInfoEntity>) {
        if (domains.isEmpty()) {
            deleteAllDomainsForLogin(loginId)
            return
        }

        val adjusted = domains.map { it.copy(loginId = loginId) }.toSet()
        upsertAll(adjusted)
        deleteAllDomainsForLogin(loginId, adjusted.map { it.value }.toSet())
    }
}

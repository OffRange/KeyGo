package de.davis.keygo.core.item.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import de.davis.keygo.core.item.data.local.entity.CreditCardEntity
import de.davis.keygo.core.item.data.local.pojo.CreditCardProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CreditCardDao {

    @Upsert
    suspend fun upsert(creditCard: CreditCardEntity)

    @Transaction
    @Query("SELECT * FROM credit_card WHERE id = :id")
    fun observeById(id: ItemId): Flow<CreditCardProjection?>

    @Transaction
    @Query("SELECT * FROM credit_card WHERE id = :id")
    suspend fun getById(id: ItemId): CreditCardProjection?

    @Transaction
    @Query("SELECT * FROM credit_card WHERE id IN (SELECT id FROM item WHERE vault_id = :vaultId)")
    suspend fun getByVault(vaultId: VaultId): List<CreditCardProjection>
}

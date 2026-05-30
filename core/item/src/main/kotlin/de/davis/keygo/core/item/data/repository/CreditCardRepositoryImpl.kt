package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.CreditCardDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.mapper.toCreditCardEntity
import de.davis.keygo.core.item.data.mapper.toData
import de.davis.keygo.core.item.data.mapper.toDomain
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class CreditCardRepositoryImpl(
    private val database: ItemDatabase,
    private val itemDao: ItemDao,
    private val creditCardDao: CreditCardDao,
) : CreditCardRepository {

    override suspend fun createOrUpdateCreditCard(card: CreditCard): Result<ItemId, Throwable> =
        runCatching {
            database.withTransaction {
                itemDao.upsert(card.toData())
                creditCardDao.upsert(card.toCreditCardEntity())

                card.id
            }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) },
        )

    override fun observeCreditCardById(itemId: ItemId): Flow<CreditCard?> =
        creditCardDao.observeById(itemId).map { it?.toDomain() }

    override suspend fun getCreditCardById(itemId: ItemId): CreditCard? =
        creditCardDao.getById(itemId)?.toDomain()
}
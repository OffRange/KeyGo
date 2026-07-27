package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.domain.repository.ItemTransactionRunner
import org.koin.core.annotation.Single

@Single
internal class ItemTransactionRunnerImpl(
    private val database: ItemDatabase,
) : ItemTransactionRunner {

    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.withTransaction { block() }
}

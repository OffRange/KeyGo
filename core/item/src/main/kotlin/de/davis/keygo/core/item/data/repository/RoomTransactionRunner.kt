package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.domain.TransactionRunner
import org.koin.core.annotation.Single

@Single
internal class RoomTransactionRunner(
    private val database: ItemDatabase,
) : TransactionRunner {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction { block() }
}

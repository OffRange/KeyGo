package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.repository.ItemTransactionRunner

/**
 * Runs the block inline with no real transaction.
 *
 * Records how many times a transaction was opened so tests can assert a batch was committed as
 * one unit rather than per item. Set [failWith] to make the next call throw before running the
 * block, which stands in for a transaction that could not be opened.
 */
class FakeItemTransactionRunner : ItemTransactionRunner {

    var transactionCount: Int = 0
        private set

    /** Thrown by the next [inTransaction] call, then cleared. */
    var failWith: Throwable? = null

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        failWith?.let { failWith = null; throw it }
        transactionCount++
        return block()
    }
}

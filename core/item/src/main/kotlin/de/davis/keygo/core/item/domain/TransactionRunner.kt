package de.davis.keygo.core.item.domain

/**
 * Runs a block of persistence work as a single atomic unit: either every write inside [block]
 * commits, or none of them do. If the calling coroutine is cancelled or [block] throws, the
 * transaction rolls back.
 *
 * Nested calls join the outermost transaction rather than opening a new one, so callers can safely
 * compose operations that already run their own transactions internally.
 */
interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

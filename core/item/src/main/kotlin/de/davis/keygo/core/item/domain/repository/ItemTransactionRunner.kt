package de.davis.keygo.core.item.domain.repository

/**
 * Runs a block of item writes as a single database transaction.
 *
 * Exists so callers outside `:core:item` can commit a batch atomically without reaching for
 * `ItemDatabase`, which is internal to this module. Nesting is safe: Room's transaction support
 * is reentrant on the same connection, so repository methods that already open their own
 * transaction join the outer one rather than starting a second.
 */
interface ItemTransactionRunner {

    suspend fun <R> inTransaction(block: suspend () -> R): R
}

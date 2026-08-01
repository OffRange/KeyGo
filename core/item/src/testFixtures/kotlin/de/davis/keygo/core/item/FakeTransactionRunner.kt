package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.repository.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory [TransactionRunner] for tests: it executes [block] without a real transaction and
 * records how many times a transaction was opened.
 *
 * - [enteredCount] is the number of times [runInTransaction] was invoked. Assert it equals 1 to
 *   verify a caller wraps all of its writes in a single atomic unit rather than one per item.
 * - Nested calls increment the count like any other; the fakes it wraps do not model rollback, so
 *   tests using it verify wrapping structure, not the rollback guarantee (that is Room's, covered by
 *   the real [TransactionRunner] implementation).
 * - [block] runs in a *separate coroutine*, mirroring Room's `withTransaction`, which dispatches
 *   onto its own transaction thread. This is not incidental: a pass-through fake that ran [block]
 *   inline let a `Flow` invariant violation reach production, because callers emitting progress
 *   from inside a transaction were emitting across a coroutine boundary and only the real Room
 *   implementation exposed it. Keep the context switch.
 */
class FakeTransactionRunner : TransactionRunner {

    var enteredCount = 0
        private set

    var failWith: Throwable? = null

    override suspend fun <R> runInTransaction(block: suspend () -> R): R {
        failWith?.let { failWith = null; throw it }
        enteredCount++
        return withContext(Dispatchers.Default) { block() }
    }
}

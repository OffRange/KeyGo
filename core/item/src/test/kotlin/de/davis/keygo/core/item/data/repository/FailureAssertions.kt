package de.davis.keygo.core.item.data.repository

import kotlin.test.assertSame

/**
 * Asserts that [actual] reports the failure raised as [expected].
 *
 * A transaction runner runs its block in a separate coroutine, and kotlinx.coroutines rebuilds an
 * exception that crosses a coroutine boundary so its stack trace points back at the caller. The
 * rebuilt exception keeps the original class and message and holds the original as its cause, but
 * it is a new instance. Comparing references directly would therefore only hold for a runner that
 * skipped the context switch, which neither Room nor [de.davis.keygo.core.item.FakeTransactionRunner]
 * does.
 */
internal fun assertFailedWith(expected: Throwable, actual: Throwable?) {
    val raised = if (actual === expected) actual else actual?.cause
    assertSame(
        expected,
        raised,
        "expected the failure to carry $expected, but was $actual",
    )
}

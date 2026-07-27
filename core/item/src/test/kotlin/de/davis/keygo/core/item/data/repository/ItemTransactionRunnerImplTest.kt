package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Verifies that [ItemTransactionRunnerImpl] delegates to [ItemDatabase.withTransaction]
 * transparently: it returns whatever the block returns, propagates whatever the block throws, and
 * invokes the block exactly once inside the transaction.
 *
 * This does not cover real SQLite commit/rollback. That is Room's own behaviour, and this project
 * has no way to exercise it in a JVM unit test without Robolectric, which is not used here.
 */
internal class ItemTransactionRunnerImplTest {

    private val database = mockk<ItemDatabase>()
    private val runner = ItemTransactionRunnerImpl(database)

    @BeforeTest
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `returns the block result`() = runTest {
        assertEquals(42, runner.inTransaction { 42 })
    }

    @Test
    fun `propagates an exception thrown inside the block`() = runTest {
        val error = IllegalStateException("boom")

        val thrown = assertFailsWith<IllegalStateException> {
            runner.inTransaction { throw error }
        }

        assertEquals(error, thrown)
    }

    @Test
    fun `invokes the block exactly once inside the transaction`() = runTest {
        var invocations = 0

        runner.inTransaction { invocations++ }

        assertEquals(1, invocations)
        coVerify(exactly = 1) { database.withTransaction(any<suspend () -> Any?>()) }
    }
}

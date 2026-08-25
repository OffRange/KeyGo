package de.davis.keygo.core.item.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

/**
 * Verifies that [TransactionRunnerImpl] delegates to Room transparently: it returns whatever the
 * block returns, propagates whatever the block throws, invokes the block exactly once, and rolls
 * back what the block wrote when it throws.
 *
 * This runs against a real in-memory database on the bundled SQLite driver, the same setup the DAO
 * tests use, so the rollback is SQLite's own rather than a stub standing in for it.
 */
internal class TransactionRunnerImplTest {

    private lateinit var database: ItemDatabase
    private lateinit var runner: TransactionRunnerImpl

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(mockk(relaxed = true), ItemDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        runner = TransactionRunnerImpl(database)
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `returns the block result`() = runTest {
        assertEquals(42, runner.runInTransaction { 42 })
    }

    @Test
    fun `propagates an exception thrown inside the block`() = runTest {
        val error = IllegalStateException("boom")

        val thrown = assertFailsWith<IllegalStateException> {
            runner.runInTransaction { throw error }
        }

        assertFailedWith(error, thrown)
    }

    @Test
    fun `invokes the block exactly once`() = runTest {
        var invocations = 0

        runner.runInTransaction { invocations++ }

        assertEquals(1, invocations)
    }

    @Test
    fun `rolls back what the block wrote when it throws`() = runTest {
        val vaultId = newVaultId()

        assertFailsWith<IllegalStateException> {
            runner.runInTransaction {
                database.vaultDao().insert(vault(vaultId))
                error("boom")
            }
        }

        assertNull(database.vaultDao().getVaultMetadata(vaultId))
    }

    private fun vault(id: VaultId) = VaultEntity(
        id = id,
        name = "Vault",
        icon = Vault.Icon.Person,
        createdAt = 0L,
        keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
    )
}

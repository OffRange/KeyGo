package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeItemTransactionRunner
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.migration.legacy_data.data.FAKE_LEGACY_KEY
import de.davis.keygo.migration.legacy_data.data.FakeLegacyCipher
import de.davis.keygo.migration.legacy_data.data.FakeLegacyItemRepository
import de.davis.keygo.migration.legacy_data.data.FakeLegacyKeyRepository
import de.davis.keygo.migration.legacy_data.data.FakeRegistrableDomainResolver
import de.davis.keygo.migration.legacy_data.domain.crypto.LegacyCipher
import de.davis.keygo.migration.legacy_data.domain.mapper.LegacyItemConverter
import de.davis.keygo.migration.legacy_data.domain.model.LegacyDetail
import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.model.LegacyRowFailure
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrateLegacyDataUseCaseTest {

    private val vaultId: VaultId = newVaultId()

    private val legacyRepository = FakeLegacyItemRepository()
    private val keyRepository = FakeLegacyKeyRepository()
    private val transactionRunner = FakeItemTransactionRunner()
    private val loginRepository = FakeLoginRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()

    private fun useCase(
        cipher: LegacyCipher = FakeLegacyCipher(),
        scopeProvider: CryptographicScopeProvider =
            FakeCryptographicScopeProvider(FakeItemRepository()),
    ) = MigrateLegacyDataUseCase(
        legacyItemRepository = legacyRepository,
        legacyKeyRepository = keyRepository,
        converter = LegacyItemConverter(cipher, FakeRegistrableDomainResolver()),
        cryptographicScopeProvider = scopeProvider,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        upsertVaultItem = UpsertVaultItemUseCase(loginRepository, creditCardRepository),
        transactionRunner = transactionRunner,
    )

    private suspend fun seedVault() {
        vaultRepository.seed(
            Vault(
                id = vaultId,
                name = "Default Vault",
                keyInformation = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
                icon = Vault.Icon.Default,
            ),
        )
        vaultContextRepository.setContextAndLastInteracted(vaultId)
    }

    private fun legacyItem(id: Long, title: String, detail: LegacyDetail) = LegacyItem(
        legacyId = id,
        title = title,
        favorite = false,
        createdAt = 1_700_000_000_000L,
        modifiedAt = null,
        tags = emptySet(),
        detail = detail,
    )

    private fun password(id: Long, title: String) = legacyItem(
        id = id,
        title = title,
        detail = LegacyDetail.Password(
            username = "ada",
            origin = "https://example.com",
            password = "hunter2".encodeToByteArray(),
            strength = null,
        ),
    )

    private fun creditCard(id: Long, title: String) = legacyItem(
        id = id,
        title = title,
        detail = LegacyDetail.CreditCard(
            firstName = "Ada",
            lastName = "Lovelace",
            cardNumber = "4111111111111111",
            cvv = "123",
            expirationDate = "05/30",
        ),
    )

    private fun seedRows(
        items: List<LegacyItem> = emptyList(),
        failures: List<LegacyRowFailure> = emptyList(),
    ) {
        legacyRepository.readResult =
            Result.Success(LegacyReadResult(items, failures, FAKE_LEGACY_KEY))
    }

    /**
     * A clean install reaches the same branch an emptied file does, by way of a provider with no
     * file to hand out. What keeps it apart is that there is nothing to delete: the deletion answers
     * no, and the alias stays put rather than being cleaned up on an install that never ran v1.
     */
    @Test
    fun `reports nothing to migrate when the legacy file is absent`() = runTest {
        legacyRepository.readResult = Result.Failure(LegacyReadFailure.DatabaseEmpty)
        legacyRepository.deleteSucceeds = false

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase()())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    /**
     * The alias goes with the file. An already-imported v1 file reaches this branch on the run
     * after the one that emptied it, and leaving the key behind there would strand it in the
     * Keystore with nothing left able to reach it. Safe on the same evidence the deletion is: the
     * rows were counted and there are none for the key to have protected.
     */
    @Test
    fun `deletes an empty database and its legacy key, and reports nothing to migrate`() = runTest {
        legacyRepository.readResult = Result.Failure(LegacyReadFailure.DatabaseEmpty)

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase()())
        assertTrue(legacyRepository.databaseDeleted)
        assertTrue(keyRepository.deleted)
    }

    /**
     * Never folded into the branch above. A file that will not open says nothing about what is in
     * it, and answering "nothing to migrate" would delete a copy nobody has ever read.
     */
    @Test
    fun `leaves an unreadable database exactly where it found it`() = runTest {
        legacyRepository.readResult = Result.Failure(LegacyReadFailure.DatabaseUnreadable)

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `imports every row then deletes the database and the legacy key`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One"), password(2L, "Two")))

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(2, outcome.report.migratedItems)
        assertFalse(outcome.report.hasFailures)
        assertFalse(outcome.report.fileRetained)
        assertEquals(listOf(1L, 2L), legacyRepository.prunedIds)
        assertTrue(legacyRepository.databaseDeleted)
        assertTrue(keyRepository.deleted)
        assertEquals(2, loginRepository.observeLogins().first().size)
    }

    @Test
    fun `imports both v1 types in one run`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "Login"), creditCard(2L, "Card")))

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(2, outcome.report.migratedItems)
        assertEquals(1, loginRepository.observeLogins().first().size)
        assertEquals(listOf(1L, 2L), legacyRepository.prunedIds)
    }

    @Test
    fun `commits the whole batch inside a single transaction`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One"), password(2L, "Two"), password(3L, "Three")))

        useCase()()

        assertEquals(1, transactionRunner.transactionCount)
    }

    @Test
    fun `keeps the database and the key when a row failed to read`() = runTest {
        seedVault()
        seedRows(
            items = listOf(password(1L, "Fine")),
            failures = listOf(LegacyRowFailure(2L, "Broken", LegacyFailureReason.Unreadable)),
        )

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.migratedItems)
        assertEquals(1, outcome.report.failures.size)
        assertEquals(listOf(1L), legacyRepository.prunedIds)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `keeps the file when a row failed even if the file claims to be empty`() = runTest {
        seedVault()
        seedRows(
            items = listOf(password(1L, "Fine")),
            failures = listOf(LegacyRowFailure(2L, "Broken", LegacyFailureReason.Unreadable)),
        )
        // The count is forced to claim the file is empty. A row we know did not come across
        // outranks it: a file we could not read in full is not deleted on a row count's say-so.
        legacyRepository.countResult = Result.Success(0)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.failures.size)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `reports a row whose nested password will not decrypt without importing it`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "Fine")))

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(
            useCase(cipher = FakeLegacyCipher.Failing)(),
        )

        assertEquals(0, outcome.report.migratedItems)
        assertEquals(
            LegacyFailureReason.UndecryptablePassword,
            outcome.report.failures.single().reason,
        )
        assertTrue(legacyRepository.prunedIds.isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `fails without importing when the batch write throws`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        transactionRunner.failWith = IllegalStateException("database is locked")

        val outcome = assertIs<LegacyMigrationOutcome.Failed>(useCase()())

        assertEquals("database is locked", outcome.cause.message)
        assertTrue(legacyRepository.prunedIds.isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `fails without pruning when one item cannot be written`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One"), password(2L, "Two")))
        loginRepository.createOrUpdateError = IllegalStateException("constraint failed")

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())

        assertTrue(legacyRepository.prunedIds.isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `fails when no vault is available to import into`() = runTest {
        seedRows(items = listOf(password(1L, "One")))

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    /**
     * A scope that will not open stands in for a vault key that will not unwrap. That says nothing
     * about any one row, so the run has to stop rather than blame the rows.
     */
    @Test
    fun `fails without importing when no cryptographic scope can be opened`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One"), password(2L, "Two")))
        val unopenable = FakeCryptographicScopeProvider(FakeItemRepository())
            .apply { itemScopeFailure = CryptoScopeError.IdNotFound }

        assertIs<LegacyMigrationOutcome.Failed>(useCase(scopeProvider = unopenable)())

        assertEquals(0, transactionRunner.transactionCount)
        assertTrue(legacyRepository.prunedIds.isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `deletes the database when the legacy file held no rows at all`() = runTest {
        seedVault()
        seedRows()

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(0, outcome.report.migratedItems)
        assertTrue(legacyRepository.databaseDeleted)
        assertTrue(keyRepository.deleted)
    }

    @Test
    fun `a second run after a partial failure imports nothing new`() = runTest {
        seedVault()
        seedRows(
            failures = listOf(LegacyRowFailure(2L, "Broken", LegacyFailureReason.Unreadable)),
        )

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(0, outcome.report.migratedItems)
        assertTrue(loginRepository.observeLogins().first().isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `keeps the file when the whole read failed rather than treating it as empty`() = runTest {
        seedVault()
        legacyRepository.readResult = Result.Failure(LegacyReadFailure.KeyUnavailable)

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())

        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
        assertEquals(0, transactionRunner.transactionCount)
    }

    @Test
    fun `keeps the file when the read failed because it could not be opened`() = runTest {
        seedVault()
        legacyRepository.readResult = Result.Failure(LegacyReadFailure.DatabaseUnreadable)

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())

        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
    }

    @Test
    fun `keeps the file when the imported rows could not be pruned`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        legacyRepository.pruneResult = Result.Failure(LegacyReadFailure.DatabaseUnreadable)
        // The count is forced to claim the file is empty, so the prune's own result is the only
        // thing left holding the deletion back. The two disagreeing is exactly when we trust
        // neither of them.
        legacyRepository.countResult = Result.Success(0)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.migratedItems)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
        assertTrue(
            outcome.report.fileRetained,
            "Every row imported, but the file survived. That has to be as loud as a row failure, " +
                    "or the next unlock reimports the whole vault with nothing in logcat to explain why.",
        )
    }

    @Test
    fun `keeps the file when rows are still in it after pruning`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        legacyRepository.countResult = Result.Success(1)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.migratedItems)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
        assertTrue(outcome.report.fileRetained)
    }

    @Test
    fun `keeps the file when the remaining rows cannot be counted`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        legacyRepository.countResult = Result.Failure(LegacyReadFailure.DatabaseUnreadable)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
        assertTrue(outcome.report.fileRetained)
    }

    @Test
    fun `keeps the legacy key when the database file refused to be deleted`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        legacyRepository.deleteSucceeds = false

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.migratedItems)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyRepository.deleted)
        assertTrue(outcome.report.fileRetained)
    }
}

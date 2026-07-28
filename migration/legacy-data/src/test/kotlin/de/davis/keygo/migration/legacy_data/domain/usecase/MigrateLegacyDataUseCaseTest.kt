package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeItemTransactionRunner
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyCipher
import de.davis.keygo.migration.legacy_data.data.mapper.LegacyItemConverter
import de.davis.keygo.migration.legacy_data.domain.model.LegacyDetail
import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.model.LegacyRowFailure
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseState
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Hands the blob straight back, so a legacy password arrives as its own plaintext. */
private class PassthroughLegacyCipher : LegacyCipher {
    override fun decrypt(blob: ByteArray): ByteArray? = blob
}

/** Fails every blob, which is how a v1 row whose nested password is unrecoverable is modelled. */
private class FailingLegacyCipher : LegacyCipher {
    override fun decrypt(blob: ByteArray): ByteArray? = null
}

private class StubDomainResolver : RegistrableDomainResolver {
    override fun resolve(domain: String): String? = "example.com"
}

/**
 * Refuses to open a scope at all, standing in for a vault key that will not unwrap. Nothing about
 * that is a statement about any one row, so the run has to stop rather than blame the rows.
 */
private class UnopenableScopeProvider : CryptographicScopeProvider {

    override suspend fun <R> itemScope(
        itemId: ItemId,
        block: suspend CryptographicScope.() -> R,
    ): Result<R, CryptoScopeError> = Result.Failure(CryptoScopeError.IdNotFound)

    override suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): Result<R, CryptoScopeError> = Result.Failure(CryptoScopeError.IdNotFound)

    override suspend fun rewrapItemKey(
        sourceVault: WrappedVaultKeyInformation,
        sourceItem: WrappedItemKeyInformation,
        destinationVault: WrappedVaultKeyInformation,
    ): Result<KeyInformation, CryptoScopeError> = Result.Failure(CryptoScopeError.IdNotFound)
}

class MigrateLegacyDataUseCaseTest {

    private val vaultId: VaultId = newVaultId()

    private val legacyRepository = FakeLegacyItemRepository()
    private val keyStoreCleaner = FakeLegacyKeyStoreCleaner()
    private val transactionRunner = FakeItemTransactionRunner()
    private val loginRepository = FakeLoginRepository()
    private val creditCardRepository = FakeCreditCardRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()

    private fun useCase(
        cipher: LegacyCipher = PassthroughLegacyCipher(),
        scopeProvider: CryptographicScopeProvider =
            FakeCryptographicScopeProvider(FakeItemRepository()),
    ) = MigrateLegacyDataUseCase(
        legacyItemRepository = legacyRepository,
        legacyKeyStoreCleaner = keyStoreCleaner,
        converter = LegacyItemConverter(cipher, StubDomainResolver()),
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

    private fun password(id: Long, title: String) = LegacyItem(
        legacyId = id,
        title = title,
        favorite = false,
        createdAt = 1_700_000_000_000L,
        modifiedAt = null,
        tags = emptySet(),
        detail = LegacyDetail.Password(
            username = "ada",
            origin = "https://example.com",
            password = "hunter2".encodeToByteArray(),
            strength = null,
        ),
    )

    private fun creditCard(id: Long, title: String) = LegacyItem(
        legacyId = id,
        title = title,
        favorite = false,
        createdAt = 1_700_000_000_000L,
        modifiedAt = null,
        tags = emptySet(),
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
        legacyRepository.readResult = Result.Success(LegacyReadResult(items, failures))
    }

    @Test
    fun `reports nothing to migrate when the legacy file is absent`() = runTest {
        legacyRepository.state = LegacyDatabaseState.Absent

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase()())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
        assertEquals(0, legacyRepository.readCalls)
    }

    @Test
    fun `deletes a stale non legacy database and reports nothing to migrate`() = runTest {
        legacyRepository.state = LegacyDatabaseState.NotLegacy

        assertEquals(LegacyMigrationOutcome.NothingToMigrate, useCase()())
        assertTrue(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
        assertEquals(0, legacyRepository.readCalls)
    }

    @Test
    fun `leaves an unreadable database exactly where it found it`() = runTest {
        legacyRepository.state = LegacyDatabaseState.Unreadable

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
        assertEquals(0, legacyRepository.readCalls)
    }

    @Test
    fun `imports every row then deletes the database and the legacy key`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One"), password(2L, "Two")))

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(2, outcome.report.migratedItems)
        assertFalse(outcome.report.hasFailures)
        assertEquals(listOf(1L, 2L), legacyRepository.prunedIds)
        assertTrue(legacyRepository.databaseDeleted)
        assertTrue(keyStoreCleaner.deleted)
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
    fun `reports how many rows the sanitizer had to repair`() = runTest {
        seedVault()
        legacyRepository.repairedRows = 3
        seedRows(items = listOf(password(1L, "One")))

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(3, outcome.report.repairedRows)
    }

    @Test
    fun `keeps the database and the key when a row failed to read`() = runTest {
        seedVault()
        seedRows(
            items = listOf(password(1L, "Fine")),
            failures = listOf(LegacyRowFailure(2L, "Broken", LegacyFailureReason.Undecryptable)),
        )

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.migratedItems)
        assertEquals(1, outcome.report.failures.size)
        assertEquals(listOf(1L), legacyRepository.prunedIds)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `keeps the file when a row failed even if the file claims to be empty`() = runTest {
        seedVault()
        seedRows(
            items = listOf(password(1L, "Fine")),
            failures = listOf(LegacyRowFailure(2L, "Broken", LegacyFailureReason.Undecryptable)),
        )
        // The count is forced to claim the file is empty. A row we know did not come across
        // outranks it: a file we could not read in full is not deleted on a row count's say-so.
        legacyRepository.countResult = Result.Success(0)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.failures.size)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `reports a row whose nested password will not decrypt without importing it`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "Fine")))

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(
            useCase(cipher = FailingLegacyCipher())(),
        )

        assertEquals(0, outcome.report.migratedItems)
        assertEquals(
            LegacyFailureReason.UndecryptablePassword,
            outcome.report.failures.single().reason,
        )
        assertTrue(legacyRepository.prunedIds.isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
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
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `fails without pruning when one item cannot be written`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One"), password(2L, "Two")))
        loginRepository.createOrUpdateError = IllegalStateException("constraint failed")

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())

        assertTrue(legacyRepository.prunedIds.isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `fails when no vault is available to import into`() = runTest {
        seedRows(items = listOf(password(1L, "One")))

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `fails without importing when no cryptographic scope can be opened`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One"), password(2L, "Two")))

        assertIs<LegacyMigrationOutcome.Failed>(useCase(scopeProvider = UnopenableScopeProvider())())

        assertEquals(0, transactionRunner.transactionCount)
        assertTrue(legacyRepository.prunedIds.isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `deletes the database when the legacy file held no rows at all`() = runTest {
        seedVault()
        seedRows()

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(0, outcome.report.migratedItems)
        assertTrue(legacyRepository.databaseDeleted)
        assertTrue(keyStoreCleaner.deleted)
    }

    @Test
    fun `a second run after a partial failure imports nothing new`() = runTest {
        seedVault()
        seedRows(
            failures = listOf(LegacyRowFailure(2L, "Broken", LegacyFailureReason.Undecryptable)),
        )

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(0, outcome.report.migratedItems)
        assertTrue(loginRepository.observeLogins().first().isEmpty())
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `keeps the file when the whole read failed rather than treating it as empty`() = runTest {
        seedVault()
        legacyRepository.readResult = Result.Failure(LegacyReadFailure.KeyUnavailable)

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())

        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
        assertEquals(0, transactionRunner.transactionCount)
    }

    @Test
    fun `keeps the file when the read failed because it could not be opened`() = runTest {
        seedVault()
        legacyRepository.readResult = Result.Failure(LegacyReadFailure.DatabaseUnreadable)

        assertIs<LegacyMigrationOutcome.Failed>(useCase()())

        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
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
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `keeps the file when rows are still in it after pruning`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        legacyRepository.countResult = Result.Success(1)

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.migratedItems)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `keeps the file when the remaining rows cannot be counted`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        legacyRepository.countResult = Result.Failure(LegacyReadFailure.DatabaseUnreadable)

        assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }

    @Test
    fun `keeps the legacy key when the database file refused to be deleted`() = runTest {
        seedVault()
        seedRows(items = listOf(password(1L, "One")))
        legacyRepository.deleteSucceeds = false

        val outcome = assertIs<LegacyMigrationOutcome.Migrated>(useCase()())

        assertEquals(1, outcome.report.migratedItems)
        assertFalse(legacyRepository.databaseDeleted)
        assertFalse(keyStoreCleaner.deleted)
    }
}

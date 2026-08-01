package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.repository.TransactionRunner
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.migration.legacy_data.domain.mapper.LegacyItemConverter
import de.davis.keygo.migration.legacy_data.domain.model.LegacyFailureReason
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationException
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationOutcome
import de.davis.keygo.migration.legacy_data.domain.model.LegacyMigrationReport
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.model.LegacyRowFailure
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyItemRepository
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyKeyRepository
import de.davisalessandro.keygo.rust.ItemAad
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import javax.crypto.SecretKey
import kotlin.coroutines.cancellation.CancellationException

/**
 * Imports a KeyGo v1 install's items into the current vault.
 *
 * Runs after a session is live, because every secret is re-encrypted under an item key that is
 * wrapped by the vault key, which is wrapped by the ARK. Rows that cannot be read are skipped and
 * reported rather than aborting the run, and only the rows that were imported are pruned from the
 * legacy database, so a later attempt cannot duplicate anything.
 *
 * Two acts here cannot be undone: deleting the inherited file, and deleting v1's Keystore alias.
 * Both are gated on the user's data being provably in v2 already. Every other outcome leaves the
 * file where it is, because a retry on the next unlock costs the user nothing and a wrong deletion
 * costs them everything.
 */
@Single
class MigrateLegacyDataUseCase internal constructor(
    private val legacyItemRepository: LegacyItemRepository,
    private val legacyKeyRepository: LegacyKeyRepository,
    private val converter: LegacyItemConverter,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val vaultRepository: VaultRepository,
    private val vaultContextRepository: VaultContextRepository,
    private val upsertVaultItem: UpsertVaultItemUseCase,
    private val transactionRunner: TransactionRunner,
) {

    suspend operator fun invoke(): LegacyMigrationOutcome = try {
        migrate()
    } catch (e: CancellationException) {
        // See LegacyItemRepositoryImpl.withDao.
        throw e
    } catch (e: Exception) {
        LegacyMigrationOutcome.Failed(e)
    }

    private suspend fun migrate(): LegacyMigrationOutcome {
        // The read is the gate on everything below it. A failed read is not an empty file: it is
        // not knowing what is in the file. Reading it as "no rows" would fall straight through to
        // the deletion at the end and destroy data nobody ever looked at.
        val read = when (val result = legacyItemRepository.readAll()) {
            is Result.Success -> result.success

            // Exhaustive rather than an `else`, so a failure added later cannot default into the
            // branch that deletes.
            is Result.Failure -> return when (result.error) {
                // The one branch that destroys the file, and only because it is the one that proves
                // there is nothing to destroy: the rows were counted and there are none.
                LegacyReadFailure.DatabaseEmpty -> {
                    deleteDatabaseAndKey()
                    LegacyMigrationOutcome.NothingToMigrate
                }

                LegacyReadFailure.DatabaseUnreadable, LegacyReadFailure.KeyUnavailable ->
                    LegacyMigrationOutcome.Failed(
                        LegacyMigrationException(
                            "Could not read the legacy database: ${result.error}",
                        ),
                    )
            }
        }

        val vaultId = targetVaultId()
            ?: return LegacyMigrationOutcome.Failed(
                LegacyMigrationException("No vault to import into"),
            )
        val vaultKeyInformation = vaultRepository.getKeyInformation(vaultId)
            ?: return LegacyMigrationOutcome.Failed(
                LegacyMigrationException("Vault $vaultId has no key information"),
            )

        val failures = read.failures.toMutableList()
        val converted = mutableListOf<Pair<Long, Item>>()

        for (legacyItem in read.items) {
            val itemId = newItemId()
            val item = when (
                val scoped =
                    convert(legacyItem, itemId, vaultId, vaultKeyInformation, read.legacyKey)
            ) {
                is Result.Success -> scoped.success

                // The scope itself would not open: the vault key did not unwrap, or wrapping the
                // new item key failed. That says nothing about this row and will hold for every
                // other row too, so the run stops rather than reporting the user's intact entries
                // as individually damaged and leaving a half-filled vault behind.
                is Result.Failure -> return LegacyMigrationOutcome.Failed(
                    LegacyMigrationException(
                        "Could not open a cryptographic scope to import into: ${scoped.error}",
                    ),
                )
            }

            if (item == null) {
                failures += legacyItem.failure(LegacyFailureReason.UndecryptablePassword)
                continue
            }
            converted += legacyItem.legacyId to item
        }

        // One transaction for the batch, so a write that fails part way through leaves no items
        // behind for a retry to duplicate. The throw is what rolls it back, and it also means
        // everything in `converted` is written by the time the prune below runs.
        if (converted.isNotEmpty())
            transactionRunner.inTransaction {
                for ((legacyId, item) in converted)
                    upsertVaultItem(item).onFailure {
                        throw LegacyMigrationException("Could not write legacy row $legacyId", it)
                    }
            }

        // Only the rows now provably in v2. A prune that fails leaves the file as it was, which the
        // next run re-imports; that is a duplicate at worst, and the deletion below is the only
        // thing that could turn it into a loss.
        val pruned = legacyItemRepository.prune(converted.map { it.first }).isSuccess()

        val fileDeleted =
            deleteWhenFullyImported(hasFailures = failures.isNotEmpty(), pruned = pruned)

        return LegacyMigrationOutcome.Migrated(
            LegacyMigrationReport(
                migratedItems = converted.size,
                failures = failures,
                fileRetained = !fileDeleted,
            ),
        )
    }

    /**
     * Deletes the file and v1's alias, and only once everything in the file is in v2. A count that
     * cannot be taken is not a zero. Returns whether the file actually went.
     */
    private suspend fun deleteWhenFullyImported(hasFailures: Boolean, pruned: Boolean): Boolean {
        if (hasFailures || !pruned) return false
        if (legacyItemRepository.remainingCount().getOrNull() != 0) return false

        return deleteDatabaseAndKey()
    }

    /**
     * Deletes the inherited file and v1's Keystore alias, in that order.
     *
     * The alias has to go with the file on every path that removes it, including an already-imported
     * file that now counts zero rows: otherwise the key outlives the last run able to reach it.
     * Deleting it is safe for the same reason the file is, there is no ciphertext left for it to
     * open. Gated on the file actually going, so a key never outlives rows still on disk.
     *
     * A clean install reaches this too, by way of a provider with no file to hand out. There is
     * nothing to delete, [LegacyItemRepository.deleteDatabase] says so, and the alias stays.
     */
    private fun deleteDatabaseAndKey(): Boolean {
        if (!legacyItemRepository.deleteDatabase()) return false
        legacyKeyRepository.deleteLegacyKey()
        return true
    }

    private suspend fun convert(
        legacyItem: LegacyItem,
        itemId: ItemId,
        vaultId: VaultId,
        vaultKeyInformation: KeyInformation,
        legacyKey: SecretKey,
    ): Result<Item?, CryptoScopeError> = cryptographicScopeProvider.itemScope(
        wrappedVaultKeyInformation = WrappedVaultKeyInformation(
            wrappedVaultKey = vaultKeyInformation,
            vaultId = vaultId,
        ),
        // No wrapped item key, so the scope mints a fresh one for this import. The AAD binds it to
        // the new item id and the destination vault, which is what a v1 row has never had.
        wrappedItemKeyInformation = WrappedItemKeyInformation(
            itemAad = ItemAad(itemId = itemId, vaultId = vaultId),
        ),
    ) {
        converter.convert(
            item = legacyItem,
            itemId = itemId,
            vaultId = vaultId,
            keyInformation = wrapCurrentItemKey(),
            legacyKey = legacyKey,
        )
    }

    private suspend fun targetVaultId(): VaultId? =
        vaultContextRepository.getLastInteractedVaultId()
            ?: vaultRepository.observeAllVaultMetadata().first().firstOrNull()?.vaultId

    private fun LegacyItem.failure(reason: LegacyFailureReason) =
        LegacyRowFailure(legacyId = legacyId, title = title, reason = reason)
}

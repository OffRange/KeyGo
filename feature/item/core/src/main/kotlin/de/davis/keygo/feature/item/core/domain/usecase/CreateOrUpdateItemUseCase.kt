package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertItem
import de.davis.keygo.feature.item.core.domain.model.UpsertType
import de.davisalessandro.keygo.rust.ItemAad
import kotlin.time.Clock

/**
 * Shared create/update orchestration for every vault item type. Owns the scaffolding that is
 * identical across types: name/field validation routing, crypto-scope provisioning, item-key
 * wrapping on create, vault-move rewrap on update, persistence and error mapping. Subclasses
 * supply only the type-specific seams (build + encrypt fields, fetch, emptiness, relocate).
 *
 * Secrets are bound to the item id, so moving an item between vaults rewraps only the item key
 * and never re-encrypts payloads.
 *
 * @param U the type's upsert input
 * @param I the persisted domain item
 */
abstract class CreateOrUpdateItemUseCase<U : UpsertItem, I : Item>(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val vaultRepository: VaultRepository,
    private val upsertVaultItem: UpsertVaultItemUseCase,
) {

    /** Type-specific field validation. An empty set means valid. */
    protected abstract fun validate(upsert: U): Set<ItemUpsertError>

    /** Loads the persisted item for an update, or null when the id is unknown. */
    protected abstract suspend fun fetchExisting(id: ItemId): I?

    /** Builds a brand-new item inside [CryptographicScope]; assign the supplied [keyInformation] to it. */
    protected abstract suspend fun CryptographicScope.buildCreate(
        upsert: U,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): I

    /** Applies [upsert] onto [existing] inside [CryptographicScope]. */
    protected abstract suspend fun CryptographicScope.buildUpdate(upsert: U, existing: I): I

    /** True when the built item has no meaningful content and should be rejected as [ItemUpsertError.Empty]. */
    protected open fun isEmpty(item: I, upsert: U): Boolean = false

    /** Returns a copy of [item] moved to [vaultId] with the re-wrapped [keyInformation]. */
    protected abstract fun relocate(item: I, vaultId: VaultId, keyInformation: KeyInformation): I

    /** Returns a copy of [item] with [timestamp] applied. */
    protected abstract fun touch(item: I, timestamp: Timestamp): I

    suspend operator fun invoke(upsert: U): Result<ItemId, Set<ItemUpsertError>> {
        val errors = validate(upsert)
        if (errors.isNotEmpty()) return Result.Failure(errors)

        val built = when (val type = upsert.upsertType) {
            is UpsertType.Create -> create(upsert, type.vaultId)
            is UpsertType.Update -> update(upsert, type.id, type.targetVaultId)
        }

        return when (built) {
            is Result.Success -> upsertVaultItem(built.success).mapFailure {
                setOf(ItemUpsertError.DatabaseError(it))
            }

            is Result.Failure -> Result.Failure(setOf(built.error))
        }
    }

    private suspend fun create(
        upsert: U,
        vaultId: VaultId,
    ): Result<I, ItemUpsertError> = resultBinding {
        val itemId = newItemId()

        val vaultKeyInformation = vaultRepository.getKeyInformation(vaultId)
            ?: return Result.Failure(ItemUpsertError.InvalidVaultId)
        val aad = ItemAad(itemId = itemId, vaultId = vaultId)

        val item = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vaultKeyInformation,
                vaultId = vaultId,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            buildCreate(upsert, itemId, vaultId, wrapCurrentItemKey())
        }.bind(ItemUpsertError::CryptoError)

        if (isEmpty(item, upsert)) return Result.Failure(ItemUpsertError.Empty)
        item
    }

    private suspend fun update(
        upsert: U,
        id: ItemId,
        targetVaultId: VaultId?,
    ): Result<I, ItemUpsertError> = resultBinding {
        val existing = fetchExisting(id)
            ?: return Result.Failure(ItemUpsertError.InvalidItemId)

        val sourceVaultKeyInfo = vaultRepository.getKeyInformation(existing.vaultId)
            ?: return Result.Failure(ItemUpsertError.InvalidVaultId)
        val sourceVault = WrappedVaultKeyInformation(
            wrappedVaultKey = sourceVaultKeyInfo,
            vaultId = existing.vaultId,
        )

        val built = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = sourceVault,
            wrappedItemKeyInformation = existing.wrappedItemKeyInformation(),
        ) {
            buildUpdate(upsert, existing)
        }.bind(ItemUpsertError::CryptoError)

        val item = touch(built, built.timestamp.copy(modifiedAt = Clock.System.now()))

        if (isEmpty(item, upsert)) return Result.Failure(ItemUpsertError.Empty)

        if (targetVaultId == null || targetVaultId == existing.vaultId)
            return@resultBinding item

        // Vault changed during edit: rewrap the item key under the destination vault. Encrypted
        // secrets are bound only to the item id, so they remain valid under the same item key.
        val destinationVaultKeyInfo = vaultRepository.getKeyInformation(targetVaultId)
            ?: return Result.Failure(ItemUpsertError.InvalidVaultId)

        val rewrapped = cryptographicScopeProvider.rewrapItemKey(
            sourceVault = sourceVault,
            sourceItem = existing.wrappedItemKeyInformation(),
            destinationVault = WrappedVaultKeyInformation(
                wrappedVaultKey = destinationVaultKeyInfo,
                vaultId = targetVaultId,
            ),
        ).bind(ItemUpsertError::CryptoError)

        relocate(item, targetVaultId, rewrapped)
    }
}

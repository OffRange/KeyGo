package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.model.VaultUpdater
import de.davis.keygo.core.item.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [VaultRepository] for tests.
 *
 * - Pre-populate via [seed].
 * - Force the next [createVault] call to throw by setting [createError].
 * - Flow-returning methods react to store mutations, so observers see live updates.
 */
class FakeVaultRepository : VaultRepository {

    private val store = MutableStateFlow<Map<VaultId, Vault>>(emptyMap())

    /** Exception thrown by the next [createVault] call (cleared after use). */
    var createError: Throwable? = null

    /** Initialize this repo with [vaults]. */
    fun seed(vararg vaults: Vault) {
        store.update { it + vaults.associateBy { v -> v.id } }
    }

    override suspend fun getKeyInformation(vaultId: VaultId): KeyInformation? =
        store.value[vaultId]?.keyInformation

    override suspend fun createVault(vault: Vault) {
        createError?.let { createError = null; throw it }
        store.update { it + (vault.id to vault) }
    }

    override suspend fun updateVault(vault: VaultUpdater) {
        store.value[vault.id]?.let { existing ->
            val updated = existing.copy(
                name = vault.name,
                icon = vault.icon,
            )
            store.update { it + (vault.id to updated) }
        }
    }

    override suspend fun deleteVault(vaultId: VaultId) {
        store.update { it - vaultId }
    }

    override fun observeAllVaultMetadata(): Flow<List<VaultMetadata>> =
        store.map { vaults -> vaults.values.map { it.toMetadata() } }

    fun observeVaults(): Flow<List<Vault>> = store.map { it.values.toList() }

    private fun Vault.toMetadata() = VaultMetadata(
        vaultId = id,
        name = name,
        icon = icon,
        count = 0,
    )
}

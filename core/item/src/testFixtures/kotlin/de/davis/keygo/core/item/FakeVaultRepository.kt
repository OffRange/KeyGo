package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault
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

    fun seed(vararg vaults: Vault) {
        store.update { it + vaults.associateBy { v -> v.id } }
    }

    override suspend fun createVault(vault: Vault): Vault {
        createError?.let { createError = null; throw it }
        store.update { it + (vault.id to vault) }
        return vault
    }

    override suspend fun getVault(id: VaultId): Vault? = store.value[id]

    override fun observeVaults(): Flow<List<Vault>> = store.map { it.values.toList() }
}

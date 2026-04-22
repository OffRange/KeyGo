package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.ActiveVaultKeyInformation
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [VaultRepository] for tests.
 *
 * - Pre-populate via [seed].
 * - Force the next [createAndActivateVault] call to throw by setting [createError].
 * - Flow-returning methods react to store mutations, so observers see live updates.
 */
class FakeVaultRepository : VaultRepository {

    private val store = MutableStateFlow<Map<VaultId, Vault>>(emptyMap())
    private val activeVault = MutableStateFlow<ActiveVaultKeyInformation?>(null)

    /** Exception thrown by the next [createAndActivateVault] call (cleared after use). */
    var createError: Throwable? = null

    /**
     * Initialize this repo with [vaults]. This automatically sets the first vault as active
     */
    suspend fun seed(vararg vaults: Vault) {
        store.update { it + vaults.associateBy { v -> v.id } }
        setActiveVault(vaults.first().id)
    }

    override suspend fun setActiveVault(vaultId: VaultId) {
        activeVault.update {
            ActiveVaultKeyInformation(
                keyInformation = store.value[vaultId]?.keyInformation!!,
                vaultId = vaultId
            )
        }
    }

    override suspend fun getKeyInformation(vaultId: VaultId): KeyInformation? =
        store.value[vaultId]?.keyInformation

    override suspend fun createAndActivateVault(vault: Vault): Vault {
        createError?.let { createError = null; throw it }
        store.update { it + (vault.id to vault) }
        setActiveVault(vault.id)
        return vault
    }

    override suspend fun deleteVault(vaultId: VaultId): Boolean {
        store.update {
            if (it.size == 1) return false
            it.filterKeys { id -> id != vaultId }
        }
        return true
    }

    override fun observeAllVaultMetadata(): Flow<List<VaultMetadata>> =
        store.map { vaults -> vaults.values.map { it.toMetadata() } }

    override fun observeActiveVaultId(): Flow<VaultId?> =
        activeVault.map { it?.vaultId }

    fun observeVaults(): Flow<List<Vault>> = store.map { it.values.toList() }

    private fun Vault.toMetadata() = VaultMetadata(
        vaultId = id,
        name = name,
        icon = icon,
    )
}

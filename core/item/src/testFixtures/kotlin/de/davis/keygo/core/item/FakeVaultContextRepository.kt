package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultContextRecord
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [VaultContextRepository] for tests.
 *
 * - [observeVaultContext] reflects mutations made via the setters.
 * - [getLastInteractedVaultId] throws when no last interacted vault has been recorded;
 *   pre-seed via [seedLastInteracted] when a test relies on reading it back.
 */
class FakeVaultContextRepository(
    initialContext: VaultContext = VaultContext.NoSpecific,
) : VaultContextRepository {

    private val context = MutableStateFlow(initialContext)
    private val lastInteracted = MutableStateFlow<VaultId?>(null)

    val currentContext: VaultContext get() = context.value
    val currentLastInteracted: VaultId? get() = lastInteracted.value

    fun seedLastInteracted(vaultId: VaultId) {
        lastInteracted.value = vaultId
    }

    override suspend fun setVaultContext(context: VaultContext) {
        this.context.value = context
    }

    override suspend fun setLastInteractedVault(vaultId: VaultId) {
        lastInteracted.value = vaultId
    }

    override suspend fun setContextAndLastInteracted(vaultId: VaultId) {
        context.value = VaultContext.ById(vaultId)
        lastInteracted.value = vaultId
    }

    override fun observeVaultContext(): Flow<VaultContext> = context

    override suspend fun getLastInteractedVaultId(): VaultId =
        lastInteracted.value ?: error("No last interacted vault id set")

    override suspend fun getVaultContextRecord(): VaultContextRecord = VaultContextRecord(
        context = currentContext,
        lastInteractedVaultId = getLastInteractedVaultId()
    )
}

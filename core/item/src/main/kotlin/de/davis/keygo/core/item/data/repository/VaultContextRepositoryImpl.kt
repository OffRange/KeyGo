package de.davis.keygo.core.item.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.core.item.data.local.model.ProtoVaultContextRecord
import de.davis.keygo.core.item.data.local.model.copy
import de.davis.keygo.core.item.data.local.model.protoVaultContextRecord
import de.davis.keygo.core.item.data.maper.toDomain
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultContextRecord
import de.davis.keygo.core.item.domain.model.getIdOrNull
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class VaultContextRepositoryImpl(
    private val contextDataStore: DataStore<ProtoVaultContextRecord>,
) : VaultContextRepository {

    override suspend fun setVaultContext(context: VaultContext) {
        contextDataStore.updateData {
            it.copy {
                context.getIdOrNull()?.let { id -> vaultIdContext = id.toString() }
                    ?: clearVaultIdContext()
            }
        }
    }

    override suspend fun setLastInteractedVault(vaultId: VaultId) {
        contextDataStore.updateData {
            it.copy {
                lastInteractedVaultId = vaultId.toString()
            }
        }
    }

    override suspend fun setContextAndLastInteracted(vaultId: VaultId) {
        contextDataStore.updateData {
            protoVaultContextRecord {
                vaultIdContext = vaultId.toString()
                lastInteractedVaultId = vaultId.toString()
            }
        }
    }

    override fun observeVaultContext(): Flow<VaultContext> =
        contextDataStore.data.map {
            val id = if (it.hasVaultIdContext()) VaultId.fromString(it.vaultIdContext)
            else null

            id.toDomain()
        }

    override suspend fun getLastInteractedVaultId(): VaultId =
        contextDataStore.data
            .first()
            .let { VaultId.fromString(it.lastInteractedVaultId) }

    override suspend fun getVaultContextRecord(): VaultContextRecord = contextDataStore.data
        .first()
        .let {
            val contextId = if (it.hasVaultIdContext()) VaultId.fromString(it.vaultIdContext)
            else null

            VaultContextRecord(
                context = contextId.toDomain(),
                lastInteractedVaultId = VaultId.fromString(it.lastInteractedVaultId),
            )
        }
}
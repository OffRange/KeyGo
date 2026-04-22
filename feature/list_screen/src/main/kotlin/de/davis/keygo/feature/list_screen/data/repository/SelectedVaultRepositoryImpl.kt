package de.davis.keygo.feature.list_screen.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.feature.list_screen.data.local.model.ProtoListSelection
import de.davis.keygo.feature.list_screen.data.mapper.toDomain
import de.davis.keygo.feature.list_screen.data.mapper.toProto
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import de.davis.keygo.feature.list_screen.domain.repository.SelectedVaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class SelectedVaultRepositoryImpl(
    private val dataStore: DataStore<ProtoListSelection>,
) : SelectedVaultRepository {

    override fun observe(): Flow<SelectedVault> = dataStore.data.map { it.toDomain() }

    override suspend fun set(selection: SelectedVault) {
        dataStore.updateData { selection.toProto() }
    }
}

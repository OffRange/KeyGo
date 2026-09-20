package de.davis.keygo.feature.password_health.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.feature.backup.data.local.model.ProtoBreachCheckState
import de.davis.keygo.feature.password_health.data.mapper.toDomain
import de.davis.keygo.feature.password_health.di.annotation.BreachedQualifier
import de.davis.keygo.feature.password_health.domain.model.BreachCheckState
import de.davis.keygo.feature.password_health.domain.repository.BreachCheckStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class BreachCheckStateRepositoryImpl(
    @BreachedQualifier
    private val dataStore: DataStore<ProtoBreachCheckState>,
) : BreachCheckStateRepository {

    override fun observeBreachCheckState(): Flow<BreachCheckState> = dataStore.data
        .map(ProtoBreachCheckState::toDomain)

    override suspend fun getBreachCheckState(): BreachCheckState =
        dataStore.data.first().toDomain()

    override suspend fun setBreachEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.toBuilder().setEnabled(enabled).build()
        }
    }
}

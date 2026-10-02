package de.davis.keygo.feature.password_health.data.repository

import androidx.datastore.core.DataStore
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthSettings
import de.davis.keygo.feature.password_health.data.mapper.toDomain
import de.davis.keygo.feature.password_health.di.annotation.HealthSettingsQualifier
import de.davis.keygo.feature.password_health.domain.model.HealthSettings
import de.davis.keygo.feature.password_health.domain.repository.HealthSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class HealthSettingsRepositoryImpl(
    @HealthSettingsQualifier
    private val dataStore: DataStore<ProtoHealthSettings>,
) : HealthSettingsRepository {

    override fun observeBreachCheckState(): Flow<HealthSettings> = dataStore.data
        .map(ProtoHealthSettings::toDomain)

    override suspend fun getBreachCheckState(): HealthSettings =
        dataStore.data.first().toDomain()

    override suspend fun setBreachEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.toBuilder().setBreachesEnabled(enabled).build()
        }
    }

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.toBuilder().setNotificationsEnabled(enabled).build()
        }
    }
}

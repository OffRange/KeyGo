package de.davis.keygo.feature.password_health.domain.repository

import de.davis.keygo.feature.password_health.domain.model.HealthSettings
import kotlinx.coroutines.flow.Flow

interface HealthSettingsRepository {
    fun observeBreachCheckState(): Flow<HealthSettings>
    suspend fun getBreachCheckState(): HealthSettings
    suspend fun setBreachEnabled(enabled: Boolean)
    suspend fun setNotificationEnabled(enabled: Boolean)
}

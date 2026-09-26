package de.davis.keygo.feature.password_health

import de.davis.keygo.feature.password_health.domain.model.HealthSettings
import de.davis.keygo.feature.password_health.domain.repository.HealthSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class FakeHealthSettingsRepository(
    breachesEnabled: Boolean = true,
    notificationsEnabled: Boolean = false,
) : HealthSettingsRepository {

    val state = MutableStateFlow(
        HealthSettings(
            breachesEnabled = breachesEnabled,
            notificationsEnabled = notificationsEnabled,
        )
    )

    override fun observeBreachCheckState(): Flow<HealthSettings> = state

    override suspend fun getBreachCheckState(): HealthSettings = state.value

    override suspend fun setBreachEnabled(enabled: Boolean) {
        state.update { it.copy(breachesEnabled = enabled) }
    }

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        state.update { it.copy(notificationsEnabled = enabled) }
    }
}

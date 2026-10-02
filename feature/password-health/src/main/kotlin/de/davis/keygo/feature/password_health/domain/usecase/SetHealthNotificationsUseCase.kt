package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.feature.password_health.domain.HealthCheckNotifierScheduler
import de.davis.keygo.feature.password_health.domain.Notifier
import de.davis.keygo.feature.password_health.domain.model.KeyGoNotification
import de.davis.keygo.feature.password_health.domain.repository.HealthNotificationStateRepository
import de.davis.keygo.feature.password_health.domain.repository.HealthSettingsRepository
import org.koin.core.annotation.Single

@Single
class SetHealthNotificationsUseCase(
    private val healthSettingsRepository: HealthSettingsRepository,
    private val healthNotificationStateRepository: HealthNotificationStateRepository,
    private val scheduler: HealthCheckNotifierScheduler,
    private val notifier: Notifier,
) {

    suspend operator fun invoke(enabled: Boolean) {
        healthSettingsRepository.setNotificationEnabled(enabled)
        if (enabled) scheduler.scheduleHealthReminder()
        else turnOff()
    }

    private suspend fun turnOff() {
        scheduler.cancel()
        healthNotificationStateRepository.clear()
        notifier.cancel(KeyGoNotification.Kind.NeedsAttention)
    }
}

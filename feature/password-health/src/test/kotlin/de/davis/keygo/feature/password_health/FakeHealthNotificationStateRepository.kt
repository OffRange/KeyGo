package de.davis.keygo.feature.password_health

import de.davis.keygo.feature.password_health.domain.model.HealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthReminder
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.repository.HealthNotificationStateRepository

internal class FakeHealthNotificationStateRepository(
    var state: HealthNotificationState = HealthNotificationState(),
) : HealthNotificationStateRepository {

    override suspend fun get(): HealthNotificationState = state

    override suspend fun setSnapshot(snapshot: HealthSnapshot?) {
        state = state.copy(snapshot = snapshot)
    }

    override suspend fun setReminder(reminder: HealthReminder?) {
        state = state.copy(reminder = reminder)
    }

    override suspend fun clear() {
        state = HealthNotificationState()
    }
}

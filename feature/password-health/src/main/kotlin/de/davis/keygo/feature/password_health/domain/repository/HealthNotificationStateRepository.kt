package de.davis.keygo.feature.password_health.domain.repository

import de.davis.keygo.feature.password_health.domain.model.HealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthReminder
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot

interface HealthNotificationStateRepository {
    suspend fun get(): HealthNotificationState
    suspend fun setSnapshot(snapshot: HealthSnapshot?)
    suspend fun setReminder(reminder: HealthReminder?)
    suspend fun clear()
}

package de.davis.keygo.feature.password_health.data.mapper

import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthSettings
import de.davis.keygo.feature.password_health.domain.model.HealthSettings

internal fun ProtoHealthSettings.toDomain() = HealthSettings(
    breachesEnabled = breachesEnabled,
    notificationsEnabled = notificationsEnabled
)

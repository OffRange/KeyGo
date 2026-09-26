package de.davis.keygo.feature.password_health.domain.model

data class HealthSettings(
    val breachesEnabled: Boolean,
    val notificationsEnabled: Boolean,
)

package de.davis.keygo.feature.password_health.domain.model

import kotlin.time.Instant

data class HealthNotificationState(
    val snapshot: HealthSnapshot? = null,
    val reminder: HealthReminder? = null,
)

data class HealthSnapshot(
    val attentionCount: Int,
    val breachedCount: Int,
    val vault: VaultFingerprint,
)

data class HealthReminder(
    val attentionCount: Int,
    val breachedCount: Int,
    val sentAt: Instant,
    val repeats: Int,
)

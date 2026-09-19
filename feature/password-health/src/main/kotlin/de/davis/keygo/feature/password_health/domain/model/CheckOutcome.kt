package de.davis.keygo.feature.password_health.domain.model

data class CheckOutcome(
    val findings: List<HealthFinding>,
    val gap: CheckGap? = null,
)

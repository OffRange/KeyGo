package de.davis.keygo.feature.password_health.domain.model

enum class RelationType(val severity: FindingSeverity) {
    Reused(FindingSeverity.High),
    Similar(FindingSeverity.Medium),
}

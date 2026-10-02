package de.davis.keygo.feature.password_health.domain.model

sealed interface GapReason {
    data object Disabled : GapReason
    data object Unreachable : GapReason
    data object Failed : GapReason
}

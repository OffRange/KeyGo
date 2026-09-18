package de.davis.keygo.feature.password_health.domain.model

sealed interface CheckKind {
    data object Strength : CheckKind
    data object Reuse : CheckKind
    data object Breach : CheckKind
    data object Similarity : CheckKind
}

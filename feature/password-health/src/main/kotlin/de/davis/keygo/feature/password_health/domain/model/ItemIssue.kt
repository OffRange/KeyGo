package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.model.PasswordScore

sealed interface ItemIssue {
    val severity: FindingSeverity

    data class Weak(val score: PasswordScore) : ItemIssue {
        override val severity = FindingSeverity.Medium
    }

    data class Breached(val occurrences: Int) : ItemIssue {
        override val severity = FindingSeverity.Critical
    }
}

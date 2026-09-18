package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate

interface PasswordHealthChecker {
    val type: CheckKind

    suspend fun check(candidates: List<PasswordCandidate>): List<HealthFinding>
}

package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.domain.model.CheckError
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate

interface PasswordHealthChecker {
    val type: CheckKind

    suspend fun check(candidates: List<PasswordCandidate>): Result<CheckOutcome, CheckError>
}

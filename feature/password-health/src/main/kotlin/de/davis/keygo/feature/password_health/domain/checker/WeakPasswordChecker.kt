package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.domain.model.CheckError
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import org.koin.core.annotation.Single

@Single
internal class WeakPasswordChecker : PasswordHealthChecker {
    override val type = CheckKind.Strength

    override suspend fun check(
        candidates: List<PasswordCandidate>,
    ): Result<CheckOutcome, CheckError> = Result.Success(
        CheckOutcome(
            findings = candidates.filter { it.score in WEAK_SCORES }.map {
                HealthFinding.Item(
                    id = it.id,
                    issue = ItemIssue.Weak(score = it.score),
                )
            },
        ),
    )

    companion object {
        private val WEAK_SCORES = setOf(PasswordScore.Ridiculous, PasswordScore.Weak)
    }
}

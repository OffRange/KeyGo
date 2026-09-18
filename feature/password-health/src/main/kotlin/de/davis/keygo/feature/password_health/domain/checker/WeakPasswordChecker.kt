package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import org.koin.core.annotation.Single

@Single
internal class WeakPasswordChecker : PasswordHealthChecker {
    override val type = CheckKind.Strength

    override suspend fun check(candidates: List<PasswordCandidate>): List<HealthFinding> =
        candidates.filter { it.score in WEAK_SCORES }.map {
            HealthFinding.Item(
                id = it.id,
                issue = ItemIssue.Weak(score = it.score)
            )
        }

    companion object {
        private val WEAK_SCORES = setOf(PasswordScore.Ridiculous, PasswordScore.Weak)
    }
}

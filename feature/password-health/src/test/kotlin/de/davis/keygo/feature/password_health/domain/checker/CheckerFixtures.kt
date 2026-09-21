package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import java.util.UUID

/** Candidates whose ids carry their position, so a finding can be read back as text. */
internal fun candidates(passwords: List<String>): List<PasswordCandidate> =
    passwords.mapIndexed { index, password ->
        PasswordCandidate(
            id = UUID(0L, index.toLong()),
            title = "item $index",
            username = null,
            score = PasswordScore.Moderate,
            password = password.toCharArray(),
            urls = emptyList(),
        )
    }

internal fun candidates(vararg passwords: String) = candidates(passwords.toList())

/** The flagged groups, as the passwords themselves, so a failure reads plainly. */
internal suspend fun flag(passwords: List<String>): Set<Set<String>> {
    val candidates = candidates(passwords)
    val byId = candidates.associateBy({ it.id }, { passwords[it.id.leastSignificantBits.toInt()] })
    return SimilarPasswordChecker().check(candidates)
        .findings
        .filterIsInstance<HealthFinding.Relation>()
        .mapTo(mutableSetOf()) { finding ->
            finding.relatedItemIds.mapTo(mutableSetOf()) { byId.getValue(it) }
        }
}

internal suspend fun flag(vararg passwords: String) = flag(passwords.toList())

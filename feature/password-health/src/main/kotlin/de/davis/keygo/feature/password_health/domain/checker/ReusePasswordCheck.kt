package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.RelationType
import org.koin.core.annotation.Single

@Single
internal class ReusePasswordCheck : PasswordHealthChecker {

    override val type = CheckKind.Reuse

    override suspend fun check(candidates: List<PasswordCandidate>): CheckOutcome = CheckOutcome(
        findings = candidates.groupBy { PasswordKey(it.password) }
            .values
            .filter { it.size > 1 }
            .map { sharing ->
                HealthFinding.Relation(
                    relatedItemIds = sharing.mapTo(mutableSetOf()) { it.id },
                    type = RelationType.Reused,
                )
            },
    )
}

private class PasswordKey(private val value: CharArray) {
    override fun equals(other: Any?) = other is PasswordKey && value.contentEquals(other.value)
    override fun hashCode() = value.contentHashCode()
}

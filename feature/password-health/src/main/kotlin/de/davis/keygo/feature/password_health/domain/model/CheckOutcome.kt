package de.davis.keygo.feature.password_health.domain.model

data class CheckOutcome(
    val findings: List<HealthFinding> = emptyList(),
    val gap: CheckGap? = null,
) {

    companion object {

        fun skipped(reason: GapReason, candidates: List<PasswordCandidate>) = CheckOutcome(
            gap = CheckGap(
                reason = reason,
                unchecked = candidates.mapTo(mutableSetOf()) { it.id },
            ),
        )
    }
}

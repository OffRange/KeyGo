package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.model.PasswordScore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemHealthTest {

    @Test
    fun anItemWithoutIssuesHasNoSeverity() {
        val health = health()

        assertNull(health.maxSeverity)
        assertNull(health.breach)
        assertNull(health.weak)
    }

    @Test
    fun aWeakPasswordIsMedium() {
        val health = health(ItemIssue.Weak(PasswordScore.Weak))

        assertEquals(FindingSeverity.Medium, health.maxSeverity)
        assertEquals(ItemIssue.Weak(PasswordScore.Weak), health.weak)
        assertNull(health.breach)
    }

    @Test
    fun aBreachedPasswordIsCritical() {
        val health = health(ItemIssue.Breached(5))

        assertEquals(FindingSeverity.Critical, health.maxSeverity)
        assertEquals(ItemIssue.Breached(5), health.breach)
        assertNull(health.weak)
    }

    @Test
    fun theGravestIssueSetsTheSeverity() {
        val health = health(ItemIssue.Weak(PasswordScore.Ridiculous), ItemIssue.Breached(1))

        assertEquals(FindingSeverity.Critical, health.maxSeverity)
        assertEquals(ItemIssue.Breached(1), health.breach)
        assertEquals(ItemIssue.Weak(PasswordScore.Ridiculous), health.weak)
    }

    @Test
    fun severitiesRankMediumBelowHighBelowCritical() {
        assertEquals(
            listOf(FindingSeverity.Medium, FindingSeverity.High, FindingSeverity.Critical),
            FindingSeverity.entries.sorted(),
        )
    }

    @Test
    fun reuseIsGraverThanSimilarity() {
        assertEquals(FindingSeverity.High, RelationType.Reused.severity)
        assertEquals(FindingSeverity.Medium, RelationType.Similar.severity)
    }

    @Test
    fun aSkippedCheckLeavesEveryCandidateUnchecked() {
        val candidates = List(3) {
            PasswordCandidate(UUID(0L, it.toLong()), PasswordScore.Strong, charArrayOf('x'))
        }

        val outcome = CheckOutcome.skipped(GapReason.Disabled, candidates)

        assertEquals(emptyList(), outcome.findings)
        assertEquals(
            CheckGap(GapReason.Disabled, candidates.mapTo(mutableSetOf()) { it.id }),
            outcome.gap,
        )
    }

    private fun health(vararg issues: ItemIssue) = ItemHealth(
        itemId = UUID(0L, 0L),
        title = "item",
        username = null,
        issues = issues.toList(),
    )
}

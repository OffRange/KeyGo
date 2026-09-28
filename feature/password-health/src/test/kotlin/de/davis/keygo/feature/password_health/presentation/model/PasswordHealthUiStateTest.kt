package de.davis.keygo.feature.password_health.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.RelatedGroup
import de.davis.keygo.feature.password_health.domain.model.RelationType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PasswordHealthUiStateTest {

    private val weak = ItemIssue.Weak(PasswordScore.Weak)
    private val breached = ItemIssue.Breached(3)

    @Test
    fun anEmptyVaultHasNoData() {
        assertEquals(PasswordHealthStatus.NO_DATA, PasswordHealthUiState().status)
    }

    @Test
    fun noPasswordsIsNoDataRatherThanAFailure() {
        val state = PasswordHealthUiState(error = PasswordHealthReportError.NoPasswords)

        assertEquals(PasswordHealthStatus.NO_DATA, state.status)
    }

    @Test
    fun anyOtherErrorIsAFailure() {
        val state = PasswordHealthUiState(
            error = PasswordHealthReportError.Unreadable,
            totalPasswordCount = 4,
        )

        assertEquals(PasswordHealthStatus.FAILED, state.status)
    }

    @Test
    fun checkedPasswordsWithoutFindingsAreAllGood() {
        assertEquals(
            PasswordHealthStatus.ALL_GOOD,
            PasswordHealthUiState(totalPasswordCount = 3).status
        )
    }

    @Test
    fun anyFindingNeedsAttention() {
        val state = PasswordHealthUiState(
            totalPasswordCount = 3,
            reportedSections = listOf(
                section(
                    FindingSeverity.Medium,
                    standalone = listOf(item(0, weak))
                )
            ),
        )

        assertEquals(PasswordHealthStatus.NEEDS_ATTENTION, state.status)
    }

    @Test
    fun fixingEveryFindingIsAllGood() {
        val state = PasswordHealthUiState(
            totalPasswordCount = 3,
            reportedSections = listOf(
                section(
                    FindingSeverity.Medium,
                    standalone = listOf(item(0, weak))
                )
            ),
            optimisticallyFixed = setOf(id(0)),
        )

        assertEquals(PasswordHealthStatus.ALL_GOOD, state.status)
        assertTrue(state.healthSections.isEmpty())
    }

    @Test
    fun phasesAreReportedAsFlags() {
        assertTrue(PasswordHealthUiState(phase = RunPhase.FirstLoad).isFirstLoad)
        assertTrue(PasswordHealthUiState(phase = RunPhase.Refresh).isRefreshing)
        PasswordHealthUiState(phase = RunPhase.Idle).let {
            assertFalse(it.isFirstLoad)
            assertFalse(it.isRefreshing)
        }
    }

    @Test
    fun theFixFlowIsExposedByStep() {
        val generating = PasswordHealthUiState(fixFlow = FixFlow.Generating(id(0)))
        val pending = PasswordHealthUiState(fixFlow = FixFlow.Pending(id(0), "new"))

        assertEquals(FixFlow.Generating(id(0)), generating.generatingFix)
        assertNull(generating.pendingFix)
        assertEquals(FixFlow.Pending(id(0), "new"), pending.pendingFix)
        assertNull(pending.generatingFix)
    }

    @Test
    fun exposesTheBreachGapAlone() {
        val gap = CheckGap(GapReason.Unreachable, setOf(id(0)))
        val state = PasswordHealthUiState(
            checkGaps = mapOf(
                CheckKind.Breach to gap,
                CheckKind.Similarity to CheckGap(GapReason.Failed, setOf(id(1))),
            ),
        )

        assertEquals(gap, state.breachGap)
        assertNull(PasswordHealthUiState().breachGap)
    }

    @Test
    fun nothingFixedLeavesTheSectionsAsTheyAre() {
        val sections = listOf(section(FindingSeverity.Medium, standalone = listOf(item(0, weak))))

        assertSame(sections, sections.withoutFixed(emptySet()))
    }

    @Test
    fun aFixedStandaloneItemLeavesItsSection() {
        val sections = listOf(
            section(FindingSeverity.Medium, standalone = listOf(item(0, weak), item(1, weak))),
        )

        val remaining = sections.withoutFixed(setOf(id(0)))

        assertEquals(listOf(id(1)), remaining.single().standalone.map { it.itemId })
    }

    @Test
    fun aSectionLeftEmptyIsDropped() {
        val sections = listOf(
            section(FindingSeverity.Critical, standalone = listOf(item(0, breached))),
            section(FindingSeverity.Medium, standalone = listOf(item(1, weak))),
        )

        val remaining = sections.withoutFixed(setOf(id(0)))

        assertEquals(listOf(FindingSeverity.Medium), remaining.map { it.severity })
    }

    @Test
    fun aGroupThatKeepsTwoMembersStaysAGroup() {
        val group = group(RelationType.Reused, item(0), item(1), item(2))
        val sections = listOf(section(FindingSeverity.High, groups = listOf(group)))

        val remaining = sections.withoutFixed(setOf(id(0))).single()

        assertEquals(
            setOf(id(1), id(2)),
            remaining.groups.single().members.mapTo(mutableSetOf()) { it.itemId },
        )
        assertTrue(remaining.standalone.isEmpty())
    }

    @Test
    fun aGroupLeftWithOneCleanMemberDisappears() {
        val sections = listOf(
            section(
                FindingSeverity.High,
                groups = listOf(group(RelationType.Reused, item(0), item(1)))
            ),
        )

        assertTrue(sections.withoutFixed(setOf(id(0))).isEmpty())
    }

    @Test
    fun aGroupLeftWithOneFlaggedMemberFallsBackToStandalone() {
        val sections = listOf(
            section(
                FindingSeverity.Critical,
                groups = listOf(group(RelationType.Reused, item(0), item(1, breached))),
                standalone = listOf(item(2, weak)),
            ),
        )

        val remaining = sections.withoutFixed(setOf(id(0))).single()

        assertTrue(remaining.groups.isEmpty())
        assertEquals(listOf(id(1), id(2)), remaining.standalone.map { it.itemId })
    }

    @Test
    fun standaloneItemsAreClusteredByTheirWorstIssueBreachedFirst() {
        val section = section(
            FindingSeverity.Critical,
            standalone = listOf(item(0, weak), item(1, breached, weak), item(2, breached)),
        )

        assertEquals(
            listOf(
                StandaloneIssue.Breached to listOf(id(1), id(2)),
                StandaloneIssue.Weak to listOf(id(0)),
            ),
            section.standaloneClusters.map { cluster -> cluster.issue to cluster.items.map { it.itemId } },
        )
    }

    @Test
    fun noStandaloneItemsMeansNoClusters() {
        val section = section(
            FindingSeverity.High,
            groups = listOf(group(RelationType.Reused, item(0), item(1)))
        )

        assertTrue(section.standaloneClusters.isEmpty())
    }

    @Test
    fun summaryCountsEveryFlaggedItemOnce() {
        val sections = listOf(
            section(
                FindingSeverity.Critical,
                groups = listOf(group(RelationType.Reused, item(0, breached, weak), item(1))),
                standalone = listOf(item(2, breached)),
            ),
            section(FindingSeverity.Medium, standalone = listOf(item(3, weak))),
        )

        assertEquals(
            HealthSummary(needsAttention = 4, weak = 2, breached = 2, reused = 2, similar = 0),
            sections.summary(),
        )
    }

    @Test
    fun summaryCountsTheItemsCoveredByEachRelation() {
        val mixed = RelatedGroup(
            id = id(0),
            members = (0..3).mapTo(mutableSetOf()) { item(it) },
            relations = setOf(
                HealthFinding.Relation(setOf(id(0), id(1)), RelationType.Reused),
                HealthFinding.Relation(setOf(id(1), id(2), id(3)), RelationType.Similar),
            ),
        )

        val summary = listOf(section(FindingSeverity.High, groups = listOf(mixed))).summary()

        assertEquals(2, summary.reused)
        assertEquals(3, summary.similar)
        assertEquals(4, summary.needsAttention)
    }

    @Test
    fun summaryOfNothingIsAllZero() {
        assertEquals(HealthSummary(0, 0, 0, 0, 0), emptyList<HealthSection>().summary())
    }

    @Test
    fun theSummaryFollowsTheFixedItems() {
        val state = PasswordHealthUiState(
            totalPasswordCount = 2,
            reportedSections = listOf(
                section(FindingSeverity.Medium, standalone = listOf(item(0, weak), item(1, weak))),
            ),
            optimisticallyFixed = setOf(id(0)),
        )

        assertEquals(1, state.summary.needsAttention)
        assertEquals(1, state.summary.weak)
    }

    private fun id(n: Int): ItemId = UUID(0L, n.toLong())

    private fun item(n: Int, vararg issues: ItemIssue) = ItemHealth(
        itemId = id(n),
        title = "item-$n",
        username = null,
        issues = issues.toList(),
    )

    private fun group(type: RelationType, vararg members: ItemHealth) = RelatedGroup(
        id = members.first().itemId,
        members = members.toSet(),
        relations = setOf(
            HealthFinding.Relation(
                members.mapTo(mutableSetOf()) { it.itemId },
                type
            )
        ),
    )

    private fun section(
        severity: FindingSeverity,
        groups: List<RelatedGroup> = emptyList(),
        standalone: List<ItemHealth> = emptyList(),
    ) = HealthSection(severity, groups, standalone)
}

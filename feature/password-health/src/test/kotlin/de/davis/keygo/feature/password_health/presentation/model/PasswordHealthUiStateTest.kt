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
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
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
    fun sectionsRunFromCriticalToMedium() {
        val report = report(
            groups = listOf(
                group(RelationType.Similar, item(0), item(1)),
                group(RelationType.Reused, item(4), item(5)),
            ),
            standalone = listOf(item(2, weak), item(3, breached)),
        )

        assertEquals(
            listOf(FindingSeverity.Critical, FindingSeverity.High, FindingSeverity.Medium),
            report.toSections().map { it.severity },
        )
    }

    @Test
    fun largerGroupsComeFirst() {
        val pair = group(RelationType.Reused, item(0, title = "A"), item(1, title = "B"))
        val trio = group(RelationType.Reused, item(2, title = "X"), item(3), item(4))

        val section = report(groups = listOf(pair, trio)).toSections().single()

        assertEquals(listOf(trio.id, pair.id), section.groups.map { it.id })
    }

    @Test
    fun groupsOfTheSameSizeFollowTheNaturalOrderOfTheirFirstMember() {
        val ten = group(RelationType.Reused, item(0, title = "Server 10"), item(1, title = "Zulu"))
        val two = group(RelationType.Reused, item(2, title = "Server 2"), item(3, title = "Zulu"))

        val section = report(groups = listOf(ten, two)).toSections().single()

        assertEquals(listOf(two.id, ten.id), section.groups.map { it.id })
    }

    @Test
    fun groupMembersAreOrderedBySeverityThenNaturally() {
        val group = group(
            RelationType.Reused,
            item(0, title = "Item 10"),
            item(1, breached, title = "Zulu"),
            item(2, title = "item 2"),
        )

        assertEquals(listOf(id(1), id(2), id(0)), group.orderedMembers.map { it.itemId })
    }

    @Test
    fun standaloneItemsAreOrderedNaturallyWhateverTheirIssueCount() {
        val section = report(
            standalone = listOf(
                item(0, breached, title = "Mail 10"),
                item(1, breached, title = "Mail 2"),
                item(2, breached, weak, title = "Zulu"),
            ),
        ).toSections().single()

        assertEquals(listOf(id(1), id(0), id(2)), section.standalone.map { it.itemId })
    }

    @Test
    fun groupsReorderWhenAFixShrinksThem() {
        val trio = group(
            RelationType.Reused,
            item(0, title = "Server 10"),
            item(1, title = "Zulu 1"),
            item(2, title = "Zulu 2"),
        )
        val pair =
            group(RelationType.Reused, item(3, title = "Server 2"), item(4, title = "Zulu 3"))
        val sections = report(groups = listOf(trio, pair)).toSections()
        assertEquals(listOf(trio.id, pair.id), sections.single().groups.map { it.id })

        val remaining = sections.withoutFixed(setOf(id(1))).single()

        assertEquals(listOf(pair.id, trio.id), remaining.groups.map { it.id })
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

    @Test
    fun theWorstSeverityIsTheTopSection() {
        val state = PasswordHealthUiState(
            totalPasswordCount = 3,
            reportedSections = listOf(
                section(
                    FindingSeverity.High,
                    groups = listOf(group(RelationType.Reused, item(0), item(1))),
                ),
                section(FindingSeverity.Medium, standalone = listOf(item(2, weak))),
            ),
        )

        assertEquals(FindingSeverity.High, state.worstSeverity)
    }

    @Test
    fun theWorstSeverityDropsOnceItsItemsAreFixed() {
        val state = PasswordHealthUiState(
            totalPasswordCount = 2,
            reportedSections = listOf(
                section(FindingSeverity.Critical, standalone = listOf(item(0, breached))),
                section(FindingSeverity.Medium, standalone = listOf(item(1, weak))),
            ),
            optimisticallyFixed = setOf(id(0)),
        )

        assertEquals(FindingSeverity.Medium, state.worstSeverity)
    }

    @Test
    fun nothingFlaggedHasNoWorstSeverity() {
        assertNull(PasswordHealthUiState(totalPasswordCount = 3).worstSeverity)
    }

    @Test
    fun theBreakdownPutsEachPasswordUnderItsWorstIssue() {
        val sections = listOf(
            section(
                FindingSeverity.Critical,
                groups = listOf(
                    group(RelationType.Similar, item(0, breached), item(1), item(2, weak)),
                ),
                standalone = listOf(item(3, breached, weak)),
            ),
            section(
                FindingSeverity.High,
                groups = listOf(group(RelationType.Reused, item(4, weak), item(5))),
            ),
            section(FindingSeverity.Medium, standalone = listOf(item(6, weak))),
        )

        assertEquals(
            SeverityBreakdown(critical = 2, high = 2, medium = 3, clean = 3),
            sections.breakdown(checked = 10),
        )
    }

    @Test
    fun aGroupMemberTakesTheSeverityOfItsOwnRelationOnly() {
        val mixed = RelatedGroup(
            id = id(0),
            members = (0..2).mapTo(mutableSetOf()) { item(it) },
            relations = setOf(
                HealthFinding.Relation(setOf(id(0), id(1)), RelationType.Reused),
                HealthFinding.Relation(setOf(id(1), id(2)), RelationType.Similar),
            ),
        )

        assertEquals(
            SeverityBreakdown(critical = 0, high = 2, medium = 1, clean = 0),
            listOf(section(FindingSeverity.High, groups = listOf(mixed))).breakdown(checked = 3),
        )
    }

    @Test
    fun unreadablePasswordsAreLeftOutOfTheBreakdown() {
        val state = PasswordHealthUiState(
            totalPasswordCount = 5,
            reportedSections = listOf(
                section(FindingSeverity.Medium, standalone = listOf(item(0, weak))),
            ),
            unreadable = setOf(id(8), id(9)),
        )

        assertEquals(
            SeverityBreakdown(critical = 0, high = 0, medium = 1, clean = 2),
            state.breakdown,
        )
    }

    @Test
    fun aFixedPasswordMovesToClean() {
        val state = PasswordHealthUiState(
            totalPasswordCount = 4,
            reportedSections = listOf(
                section(
                    FindingSeverity.Critical,
                    standalone = listOf(item(0, breached), item(1, breached)),
                ),
            ),
            optimisticallyFixed = setOf(id(0)),
        )

        assertEquals(
            SeverityBreakdown(critical = 1, high = 0, medium = 0, clean = 3),
            state.breakdown,
        )
    }

    private fun id(n: Int): ItemId = UUID(0L, n.toLong())

    private fun item(n: Int, vararg issues: ItemIssue, title: String = "item-$n") = ItemHealth(
        itemId = id(n),
        title = title,
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

    private fun report(
        groups: List<RelatedGroup> = emptyList(),
        standalone: List<ItemHealth> = emptyList(),
    ) = PasswordHealthReport(groups, standalone, totalPasswordsScanned = 10)

    private fun section(
        severity: FindingSeverity,
        groups: List<RelatedGroup> = emptyList(),
        standalone: List<ItemHealth> = emptyList(),
    ) = HealthSection(severity, groups, standalone)
}

package de.davis.keygo.feature.password_health.domain.report

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.RelationType
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthReportAssemblerTest {

    private val assembler = HealthReportAssembler()

    @Test
    fun relationsThatShareAnItemFormOneGroup() {
        val report = report(
            items = (0..3).map { storedItem(id(it)) },
            relations = listOf(
                relation(RelationType.Reused, 0, 1),
                relation(RelationType.Similar, 1, 2),
            ),
        )

        val assembled = assembler.assemble(report, logins(0..3))

        assertEquals(1, assembled.groups.size)
        assertEquals(
            setOf(id(0), id(1), id(2)),
            assembled.groups.single().members.mapTo(mutableSetOf()) { it.itemId },
        )
        assertEquals(2, assembled.groups.single().relations.size)
    }

    @Test
    fun standaloneOnlyListsItemsWithAnIssue() {
        val report = report(
            items = listOf(
                storedItem(id(0), score = PasswordScore.Weak),
                storedItem(id(1)),
                storedItem(id(2), breach = breach(3)),
            ),
        )

        val assembled = assembler.assemble(report, logins(0..2))

        assertEquals(listOf(id(2), id(0)), assembled.standalone.map { it.itemId })
        assertEquals(
            listOf(ItemIssue.Breached(3)),
            assembled.standalone.first().issues,
        )
    }

    @Test
    fun aRelationLeftWithOneLoginIsDropped() {
        val report = report(
            items = listOf(storedItem(id(0)), storedItem(id(1))),
            relations = listOf(relation(RelationType.Reused, 0, 1)),
        )

        val assembled = assembler.assemble(report, logins(0..0))

        assertEquals(emptyList(), assembled.groups)
    }

    @Test
    fun aRelationKeepsTheLoginsThatStillExist() {
        val report = report(
            items = (0..2).map { storedItem(id(it)) },
            relations = listOf(relation(RelationType.Reused, 0, 1, 2)),
        )

        val assembled = assembler.assemble(report, logins(0..1))

        assertEquals(
            setOf(id(0), id(1)),
            assembled.groups.single().relations.single().relatedItemIds,
        )
    }

    @Test
    fun aDeletedLoginLeavesNoTrace() {
        val report = report(
            items = listOf(
                storedItem(id(0), breach = breach(1), unreadable = true),
                storedItem(id(1), breach = breach(1), unreadable = true),
            ),
            gaps = mapOf(
                CheckKind.Breach to CheckGap(GapReason.Unreachable, setOf(id(0))),
                CheckKind.Similarity to CheckGap(GapReason.Failed, setOf(id(0), id(1))),
            ),
        )

        val assembled = assembler.assemble(report, logins(1..1))

        assertEquals(listOf(id(1)), assembled.standalone.map { it.itemId })
        assertEquals(setOf(id(1)), assembled.unreadable)
        assertEquals(
            mapOf<CheckKind, CheckGap>(
                CheckKind.Similarity to CheckGap(GapReason.Failed, setOf(id(1))),
            ),
            assembled.gaps,
        )
    }

    private fun logins(range: IntRange) = range.map { login(id(it)) }

    private fun relation(type: RelationType, vararg ids: Int) = HealthFinding.Relation(
        relatedItemIds = ids.mapTo(mutableSetOf(), ::id),
        type = type,
    )

    private fun report(
        items: List<StoredHealthItem>,
        relations: List<HealthFinding.Relation> = emptyList(),
        gaps: Map<CheckKind, CheckGap> = emptyMap(),
    ) = StoredHealthReport(
        items = items,
        relationalFindings = relations,
        gaps = gaps,
        breachCheckEnabled = true,
    )
}

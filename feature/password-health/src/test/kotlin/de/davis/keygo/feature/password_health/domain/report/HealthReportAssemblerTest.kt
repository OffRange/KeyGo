package de.davis.keygo.feature.password_health.domain.report

import de.davis.keygo.core.item.domain.model.DomainInfo
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
import kotlin.test.assertTrue

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

    @Test
    fun itemsCarryTheLoginTheyDescribe() {
        val report = report(items = listOf(storedItem(id(0), score = PasswordScore.Weak)))
        val login = login(id(0)).copy(
            name = "Mail",
            username = "me@example.com",
            domainInfos = setOf(DomainInfo(value = "mail.example.com", eTLD1 = "example.com")),
        )

        val item = assembler.assemble(report, listOf(login)).standalone.single()

        assertEquals("Mail", item.title)
        assertEquals("me@example.com", item.username)
        assertEquals(listOf("mail.example.com"), item.urls)
    }

    @Test
    fun aWeakBreachedPasswordListsTheBreachFirst() {
        val report = report(
            items = listOf(storedItem(id(0), score = PasswordScore.Ridiculous, breach = breach(2))),
        )

        assertEquals(
            listOf(ItemIssue.Breached(2), ItemIssue.Weak(PasswordScore.Ridiculous)),
            assembler.assemble(report, logins(0..0)).standalone.single().issues,
        )
    }

    @Test
    fun aCleanBreachLookupIsNoIssue() {
        val report = report(items = listOf(storedItem(id(0), breach = breach(0))))

        assertEquals(emptyList(), assembler.assemble(report, logins(0..0)).standalone)
    }

    @Test
    fun aNoneScoreIsNoIssue() {
        val report = report(items = listOf(storedItem(id(0), score = PasswordScore.None)))

        assertEquals(emptyList(), assembler.assemble(report, logins(0..0)).standalone)
    }

    @Test
    fun aRelatedItemIsInItsGroupNotStandalone() {
        val report = report(
            items = listOf(storedItem(id(0), breach = breach(1)), storedItem(id(1))),
            relations = listOf(relation(RelationType.Reused, 0, 1)),
        )

        val assembled = assembler.assemble(report, logins(0..1))

        assertEquals(emptyList(), assembled.standalone)
        assertEquals(
            listOf(ItemIssue.Breached(1)),
            assembled.groups.single().members.single { it.itemId == id(0) }.issues,
        )
    }

    @Test
    fun aGroupKeepsItsCleanMembers() {
        val report = report(
            items = listOf(storedItem(id(0)), storedItem(id(1))),
            relations = listOf(relation(RelationType.Similar, 0, 1)),
        )

        val group = assembler.assemble(report, logins(0..1)).groups.single()

        assertEquals(setOf(id(0), id(1)), group.members.mapTo(mutableSetOf()) { it.itemId })
        assertTrue(group.id in setOf(id(0), id(1)))
    }

    @Test
    fun separateRelationsFormSeparateGroupsGravestFirst() {
        val report = report(
            items = (0..5).map { storedItem(id(it)) }.toMutableList().apply {
                set(4, storedItem(id(4), breach = breach(1)))
            },
            relations = listOf(
                relation(RelationType.Similar, 0, 1),
                relation(RelationType.Reused, 2, 3),
                relation(RelationType.Similar, 4, 5),
            ),
        )

        val groups = assembler.assemble(report, logins(0..5)).groups

        assertEquals(
            listOf(setOf(id(4), id(5)), setOf(id(2), id(3)), setOf(id(0), id(1))),
            groups.map { group -> group.members.mapTo(mutableSetOf()) { it.itemId } },
        )
    }

    @Test
    fun standaloneItemsAreGravestFirst() {
        val report = report(
            items = listOf(
                storedItem(id(0), score = PasswordScore.Weak),
                storedItem(id(1), breach = breach(4)),
                storedItem(id(2), score = PasswordScore.Ridiculous),
            ),
        )

        assertEquals(
            id(1),
            assembler.assemble(report, logins(0..2)).standalone.first().itemId,
        )
    }

    @Test
    fun aLoginAddedSinceTheScanIsLeftOut() {
        val report = report(items = listOf(storedItem(id(0), score = PasswordScore.Weak)))

        val assembled = assembler.assemble(report, logins(0..1))

        assertEquals(listOf(id(0)), assembled.standalone.map { it.itemId })
        assertEquals(1, assembled.totalPasswordsScanned)
    }

    @Test
    fun gapsAndUnreadableItemsAreCarriedOver() {
        val gap = CheckGap(GapReason.Unreachable, setOf(id(0), id(1)))
        val report = report(
            items = listOf(storedItem(id(0), unreadable = true), storedItem(id(1))),
            gaps = mapOf(CheckKind.Breach to gap),
        )

        val assembled = assembler.assemble(report, logins(0..1))

        assertEquals(mapOf<CheckKind, CheckGap>(CheckKind.Breach to gap), assembled.gaps)
        assertEquals(setOf(id(0)), assembled.unreadable)
    }

    @Test
    fun aGapLeftWithoutLoginsIsDropped() {
        val report = report(
            items = listOf(storedItem(id(0)), storedItem(id(1))),
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Failed, setOf(id(0)))),
        )

        assertEquals(emptyMap(), assembler.assemble(report, logins(1..1)).gaps)
    }

    @Test
    fun aCleanVaultAssemblesToNothing() {
        val report = report(items = (0..2).map { storedItem(id(it)) })

        val assembled = assembler.assemble(report, logins(0..2))

        assertEquals(emptyList(), assembled.groups)
        assertEquals(emptyList(), assembled.standalone)
        assertEquals(emptySet(), assembled.unreadable)
        assertEquals(3, assembled.totalPasswordsScanned)
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

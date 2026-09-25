package de.davis.keygo.feature.password_health.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A group is one connected component over every relation edge, so a handful of copies
 * can sit inside a much larger similarity chain. What the card says about it has to be
 * what is mostly true of it.
 */
class RelatedGroupTest {

    private val ids = List(6) { UUID(0L, it.toLong()) }

    @Test
    fun namesTheRelationThatCoversMostOfTheGroup() {
        val group = group(
            relation(RelationType.Reused, ids[0], ids[1]),
            relation(RelationType.Similar, ids[1], ids[2], ids[3], ids[4], ids[5]),
        )

        assertEquals(RelationType.Similar, group.dominantRelation)
    }

    @Test
    fun namesReuseWhenReuseIsWhatCoversTheGroup() {
        val group = group(
            relation(RelationType.Reused, ids[0], ids[1], ids[2], ids[3]),
            relation(RelationType.Similar, ids[3], ids[4]),
        )

        assertEquals(RelationType.Reused, group.dominantRelation)
    }

    /** A tie goes to the finding worth acting on first. */
    @Test
    fun namesTheGraverRelationWhenBothCoverTheSameCount() {
        val group = group(
            relation(RelationType.Reused, ids[0], ids[1], ids[2]),
            relation(RelationType.Similar, ids[3], ids[4], ids[5]),
        )

        assertEquals(RelationType.Reused, group.dominantRelation)
    }

    @Test
    fun keepsTheSeverityOfTheGravestRelationInIt() {
        val group = group(
            relation(RelationType.Reused, ids[0], ids[1]),
            relation(RelationType.Similar, ids[1], ids[2], ids[3], ids[4], ids[5]),
        )

        assertEquals(FindingSeverity.High, group.maxSeverity)
    }

    @Test
    fun aBreachedMemberMakesTheGroupCritical() {
        val group = RelatedGroup(
            id = ids[0],
            members = setOf(item(ids[0], ItemIssue.Breached(1)), item(ids[1])),
            relations = setOf(relation(RelationType.Similar, ids[0], ids[1])),
        )

        assertEquals(FindingSeverity.Critical, group.maxSeverity)
    }

    @Test
    fun aGroupWithoutMemberIssuesTakesTheSeverityOfItsRelation() {
        val group = RelatedGroup(
            id = ids[0],
            members = setOf(item(ids[0]), item(ids[1])),
            relations = setOf(relation(RelationType.Similar, ids[0], ids[1])),
        )

        assertEquals(FindingSeverity.Medium, group.maxSeverity)
    }

    @Test
    fun ordersTheGravestMemberFirstAndTheCleanOnesLast() {
        val group = RelatedGroup(
            id = ids[0],
            members = setOf(
                item(ids[0]),
                item(ids[1], ItemIssue.Weak(PasswordScore.Weak)),
                item(ids[2], ItemIssue.Breached(9)),
            ),
            relations = setOf(relation(RelationType.Reused, ids[0], ids[1], ids[2])),
        )

        assertEquals(listOf(ids[2], ids[1], ids[0]), group.orderedMembers.map { it.itemId })
    }

    @Test
    fun aSingleRelationNamesTheGroup() {
        assertEquals(
            RelationType.Similar,
            group(relation(RelationType.Similar, ids[0], ids[1])).dominantRelation,
        )
    }

    private fun item(id: ItemId, vararg issues: ItemIssue) =
        ItemHealth(itemId = id, title = "item", username = null, issues = issues.toList())

    private fun group(vararg relations: HealthFinding.Relation) = RelatedGroup(
        id = ids.first(),
        members = ids.mapTo(mutableSetOf()) {
            ItemHealth(itemId = it, title = "item", username = null, issues = emptyList())
        },
        relations = relations.toSet(),
    )

    private fun relation(type: RelationType, vararg members: ItemId) =
        HealthFinding.Relation(relatedItemIds = members.toSet(), type = type)
}

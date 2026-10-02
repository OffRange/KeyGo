package de.davis.keygo.feature.password_health.domain.report

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.RelatedGroup
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import org.koin.core.annotation.Single

@Single
class HealthReportAssembler {

    fun assemble(report: StoredHealthReport, logins: List<Login>): PasswordHealthReport {
        val loginsById = logins.associateBy { it.id }

        val issuesById = report.items.filterFindings()
            .filter { it.id in loginsById }
            .associate { it.id to it.issues() }

        val relations = report.relationalFindings.mapNotNull {
            val surviving = it.relatedItemIds.filterTo(mutableSetOf()) { it in loginsById }
            if (surviving.size > 1) it.copy(relatedItemIds = surviving) else null
        }

        val uf = UnionFind<ItemId>()
        relations.forEach { relation ->
            val first = relation.relatedItemIds.first()
            relation.relatedItemIds.forEach { uf.union(first, it) }
        }

        fun health(item: StoredHealthItem): ItemHealth {
            val login = loginsById.getValue(item.id)
            return ItemHealth(
                itemId = item.id,
                title = login.name,
                username = login.username,
                issues = issuesById[item.id].orEmpty().sortedByDescending { it.severity },
                urls = login.domainInfos.map { it.value },
            )
        }

        val (related, unrelated) = report.items.partition { uf.contains(it.id) }
        val relationsById = relations.groupBy { uf.find(it.relatedItemIds.first()) }

        val groups = related.groupBy { uf.find(it.id) }
            .map { (root, members) ->
                RelatedGroup(
                    id = root,
                    members = members.mapTo(mutableSetOf(), ::health),
                    relations = relationsById.getValue(root).toSet(),
                )
            }
            .sortedByDescending { it.maxSeverity }

        val standalone = unrelated.filter { it.id in loginsById }
            .map(::health)
            .filter { it.issues.isNotEmpty() }
            .sortedByDescending { it.maxSeverity }

        val survivingGaps = report.gaps.mapNotNull { (kind, gap) ->
            val unchecked = gap.unchecked.filterTo(mutableSetOf()) { it in loginsById }
            if (unchecked.isNotEmpty()) kind to gap.copy(unchecked = unchecked) else null
        }.toMap()

        return PasswordHealthReport(
            groups = groups,
            standalone = standalone,
            totalPasswordsScanned = report.totalPasswordsScanned,
            gaps = survivingGaps,
            unreadable = report.items.filter { it.unreadable }
                .mapNotNullTo(mutableSetOf()) { it.id.takeIf { id -> id in loginsById } },
        )
    }

    private fun Collection<StoredHealthItem>.filterFindings() = filter {
        (it.score != null && !it.score.isNone) ||
                it.breachOccurrences > 0
    }

    private val StoredHealthItem.breachOccurrences
        get() = breach?.occurrences ?: 0

    private fun StoredHealthItem.issues() = buildList {
        score?.takeIf { !it.isNone }?.let { add(ItemIssue.Weak(it)) }
        if (breachOccurrences > 0) add(ItemIssue.Breached(breachOccurrences))
    }
}

package de.davis.keygo.feature.password_health.presentation.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.util.domain.comparator.NaturalOrderComparator
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReport
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.RelatedGroup
import de.davis.keygo.feature.password_health.domain.model.RelationType

internal enum class RunPhase {
    Idle,
    FirstLoad,
    Refresh,
}

internal enum class PasswordHealthStatus {
    NO_DATA,
    ALL_GOOD,
    NEEDS_ATTENTION,
    FAILED
}

data class HealthSummary(
    val needsAttention: Int,
    val weak: Int,
    val breached: Int,
    val reused: Int,
    val similar: Int,
)

internal fun List<HealthSection>.summary(): HealthSummary {
    val groups = fold(mutableListOf<RelatedGroup>()) { acc, section ->
        acc.addAll(section.groups)
        acc
    }
    val standalone = fold(mutableListOf<ItemHealth>()) { acc, section ->
        acc.addAll(section.standalone)
        acc
    }


    val flagged = groups.flatMap { it.members } + standalone

    fun countWith(predicate: (ItemIssue) -> Boolean) =
        flagged.count { item -> item.issues.any(predicate) }

    fun countRelated(type: RelationType) = groups
        .flatMap { group -> group.relations.filter { it.type == type } }
        .flatMapTo(mutableSetOf()) { it.relatedItemIds }
        .size

    return HealthSummary(
        needsAttention = flagged.size,
        weak = countWith { it is ItemIssue.Weak },
        breached = countWith { it is ItemIssue.Breached },
        reused = countRelated(RelationType.Reused),
        similar = countRelated(RelationType.Similar),
    )
}

internal data class SeverityBreakdown(
    val critical: Int,
    val high: Int,
    val medium: Int,
    val clean: Int,
)

internal fun List<HealthSection>.breakdown(checked: Int): SeverityBreakdown {
    val worst = mutableMapOf<ItemId, FindingSeverity>()
    fun flag(id: ItemId, severity: FindingSeverity?) {
        if (severity != null) worst.merge(id, severity, ::maxOf)
    }

    forEach { section ->
        section.standalone.forEach { flag(it.itemId, it.maxSeverity) }
        section.groups.forEach { group ->
            val memberIds = group.members.mapTo(mutableSetOf()) { it.itemId }
            group.members.forEach { flag(it.itemId, it.maxSeverity) }
            group.relations.forEach { relation ->
                relation.relatedItemIds.forEach {
                    if (it in memberIds) flag(it, relation.type.severity)
                }
            }
        }
    }

    val counts = worst.values.groupingBy { it }.eachCount()
    return SeverityBreakdown(
        critical = counts[FindingSeverity.Critical] ?: 0,
        high = counts[FindingSeverity.High] ?: 0,
        medium = counts[FindingSeverity.Medium] ?: 0,
        clean = (checked - worst.size).coerceAtLeast(0),
    )
}

internal data class HealthSection(
    val severity: FindingSeverity,
    val groups: List<RelatedGroup>,
    val standalone: List<ItemHealth>
) {
    val standaloneClusters: List<StandaloneCluster> = standalone
        .groupBy { if (it.breach != null) StandaloneIssue.Breached else StandaloneIssue.Weak }
        .let { byIssue ->
            StandaloneIssue.entries.mapNotNull { issue ->
                byIssue[issue]?.let { StandaloneCluster(issue, it) }
            }
        }
}

internal enum class StandaloneIssue {
    Breached,
    Weak,
}

internal data class StandaloneCluster(
    val issue: StandaloneIssue,
    val items: List<ItemHealth>,
)

internal fun PasswordHealthReport.toSections(): List<HealthSection> {
    val groupsBySeverity = groups.groupBy { it.maxSeverity }
    val standaloneBySeverity = standalone.groupBy { requireNotNull(it.maxSeverity) }

    return FindingSeverity.entries.sortedDescending().mapNotNull { severity ->
        val g = groupsBySeverity[severity].orEmpty().sortedWith(GroupOrder)
        val s = standaloneBySeverity[severity].orEmpty().sortedWith(StandaloneOrder)
        if (g.isEmpty() && s.isEmpty()) null else HealthSection(severity, g, s)
    }
}

private val GroupOrder = compareByDescending<RelatedGroup> { it.members.size }
    .thenBy(NaturalOrderComparator) { it.orderedMembers.first().title }

private val StandaloneOrder = compareByDescending<ItemHealth> { it.maxSeverity }
    .thenBy(NaturalOrderComparator) { it.title }

internal fun List<HealthSection>.withoutFixed(fixed: Set<ItemId>): List<HealthSection> {
    if (fixed.isEmpty()) return this

    return mapNotNull { section ->
        val groups = mutableListOf<RelatedGroup>()
        val unrelated = mutableListOf<ItemHealth>()

        section.groups.forEach { group ->
            val members = group.members.filterNot { it.itemId in fixed }
            if (members.size >= MinimumGroupSize) groups += group.copy(members = members.toSet())
            else unrelated += members.filter { it.issues.isNotEmpty() }
        }

        val standalone = (section.standalone.filterNot { it.itemId in fixed } + unrelated)
            .sortedWith(StandaloneOrder)

        if (groups.isEmpty() && standalone.isEmpty()) null
        else section.copy(groups = groups.sortedWith(GroupOrder), standalone = standalone)
    }
}

private const val MinimumGroupSize = 2

@Stable
internal data class PasswordHealthUiState(
    val phase: RunPhase = RunPhase.Idle,
    val breachCheckEnabled: Boolean = false,
    val notificationEnabled: Boolean = false,
    val totalPasswordCount: Int = 0,
    val reportedSections: List<HealthSection> = emptyList(),
    val checkGaps: Map<CheckKind, CheckGap> = emptyMap(),
    val unreadable: Set<ItemId> = emptySet(),
    val error: PasswordHealthReportError? = null,
    val fixFlow: FixFlow? = null,
    val optimisticallyFixed: Set<ItemId> = emptySet(),
) {
    val isFirstLoad = phase == RunPhase.FirstLoad
    val isRefreshing = phase == RunPhase.Refresh

    val generatingFix = fixFlow as? FixFlow.Generating
    val pendingFix = fixFlow as? FixFlow.Pending

    val healthSections = reportedSections.withoutFixed(optimisticallyFixed)

    val summary by lazy { healthSections.summary() }

    val breakdown by lazy { healthSections.breakdown(totalPasswordCount - unreadable.size) }

    val worstSeverity: FindingSeverity? = healthSections.firstOrNull()?.severity

    val breachGap: CheckGap? = checkGaps[CheckKind.Breach]

    val status = when {
        // An empty vault is not a failure, it just has nothing to say yet.
        error == PasswordHealthReportError.NoPasswords -> PasswordHealthStatus.NO_DATA
        error != null -> PasswordHealthStatus.FAILED
        totalPasswordCount == 0 -> PasswordHealthStatus.NO_DATA
        healthSections.isNotEmpty() -> PasswordHealthStatus.NEEDS_ATTENTION
        else -> PasswordHealthStatus.ALL_GOOD
    }

    val showsFindings = !isFirstLoad && status == PasswordHealthStatus.NEEDS_ATTENTION

    val issueCounts by lazy { summary.issueCounts() }

    // Medium findings keep a neutral card and carry their tone in an accent, so the card reads
    // calmer than a high one without looking disabled.
    val isAccented = showsFindings && worstSeverity == FindingSeverity.Medium
}

@Composable
@ReadOnlyComposable
internal fun PasswordHealthUiState.toneColor(): Color {
    if (isFirstLoad) return MaterialTheme.colorScheme.surfaceContainerHigh
    return when (status) {
        PasswordHealthStatus.NO_DATA -> MaterialTheme.colorScheme.surfaceContainerHigh
        PasswordHealthStatus.ALL_GOOD -> MaterialTheme.colorScheme.secondaryContainer
        PasswordHealthStatus.NEEDS_ATTENTION -> when (worstSeverity) {
            FindingSeverity.Critical, null -> MaterialTheme.colorScheme.errorContainer
            FindingSeverity.High -> MaterialTheme.colorScheme.tertiaryContainer
            FindingSeverity.Medium -> MaterialTheme.colorScheme.surfaceContainerHigh
        }

        PasswordHealthStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    }
}

@Composable
@ReadOnlyComposable
internal fun PasswordHealthStatus.icon() = when (this) {
    PasswordHealthStatus.NO_DATA -> Icons.Default.HealthAndSafety
    PasswordHealthStatus.ALL_GOOD -> Icons.Default.GppGood
    PasswordHealthStatus.NEEDS_ATTENTION -> Icons.Default.GppBad
    PasswordHealthStatus.FAILED -> Icons.Default.GppMaybe
}

@Composable
@ReadOnlyComposable
internal fun PasswordHealthUiState.verdict(): String {
    if (isFirstLoad) return stringResource(R.string.checking_passwords)
    return when (status) {
        PasswordHealthStatus.NO_DATA -> stringResource(R.string.password_health_no_data)

        PasswordHealthStatus.ALL_GOOD ->
            if (checkGaps.isEmpty() && unreadable.isEmpty())
                stringResource(R.string.password_health_all_good)
            else stringResource(R.string.password_health_checked_all_good)

        PasswordHealthStatus.NEEDS_ATTENTION -> pluralStringResource(
            R.plurals.password_health_needs_attention,
            summary.needsAttention,
            summary.needsAttention
        )

        PasswordHealthStatus.FAILED ->
            error.failureReason() ?: stringResource(R.string.password_health_check_failed)
    }
}

internal data class HealthStatusStrings(
    val headline: String,
    val verdict: String? = null,
    val detail: String? = null,
)

@Composable
@ReadOnlyComposable
internal fun PasswordHealthUiState.statusCopy(): HealthStatusStrings {
    if (isFirstLoad) return HealthStatusStrings(stringResource(R.string.password_health_headline_checking))
    return when (status) {
        PasswordHealthStatus.NO_DATA -> HealthStatusStrings(
            headline = stringResource(R.string.password_health_headline_no_data),
            verdict = verdict(),
        )

        PasswordHealthStatus.ALL_GOOD -> HealthStatusStrings(
            headline = stringResource(R.string.password_health_headline_all_good),
            verdict = verdict(),
            detail = pluralStringResource(
                R.plurals.detail_line_checked,
                totalPasswordCount,
                totalPasswordCount,
            ),
        )

        PasswordHealthStatus.NEEDS_ATTENTION -> HealthStatusStrings(headline = verdict())

        PasswordHealthStatus.FAILED -> HealthStatusStrings(
            headline = stringResource(R.string.password_health_headline_failed),
            verdict = error.failureReason(),
            detail = stringResource(R.string.password_health_retry_hint),
        )
    }
}

internal data class IssueCount(
    @param:StringRes val label: Int,
    val count: Int,
)

internal fun HealthSummary.issueCounts(): List<IssueCount> = listOf(
    IssueCount(R.string.issue_count_breached, breached),
    IssueCount(R.string.issue_count_weak, weak),
    IssueCount(R.string.issue_count_reused, reused),
    IssueCount(R.string.issue_count_similar, similar),
).filter { it.count > 0 }

@Composable
@ReadOnlyComposable
internal fun PasswordHealthUiState.coverageNote(): String? {
    if (isFirstLoad || unreadable.isEmpty()) return null

    return pluralStringResource(
        R.plurals.password_health_unreadable_count,
        unreadable.size,
        unreadable.size,
    )
}

@Composable
@ReadOnlyComposable
private fun PasswordHealthReportError?.failureReason(): String? = when (this) {
    PasswordHealthReportError.Unreadable -> stringResource(R.string.password_health_unreadable)
    PasswordHealthReportError.NoPasswords, null -> null
}

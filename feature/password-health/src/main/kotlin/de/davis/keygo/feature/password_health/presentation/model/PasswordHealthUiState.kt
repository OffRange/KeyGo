package de.davis.keygo.feature.password_health.presentation.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.RelatedGroup
import de.davis.keygo.feature.password_health.domain.model.RelationType

internal enum class PasswordHealthStatus {
    NO_DATA,
    ALL_GOOD,
    NEEDS_ATTENTION
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

internal data class HealthSection(
    val severity: FindingSeverity,
    val groups: List<RelatedGroup>,
    val standalone: List<ItemHealth>
)

@Stable
internal data class PasswordHealthUiState(
    val isLoading: Boolean = false,
    val breachCheckEnabled: Boolean = false,
    val totalPasswordCount: Int = 0,
    val healthSections: List<HealthSection> = emptyList(),
    val generatePassword: Boolean = false,
) {
    val summary by lazy { healthSections.summary() }

    val status = when {
        totalPasswordCount == 0 -> PasswordHealthStatus.NO_DATA
        healthSections.isNotEmpty() -> PasswordHealthStatus.NEEDS_ATTENTION
        else -> PasswordHealthStatus.ALL_GOOD
    }
}

@Composable
@ReadOnlyComposable
internal fun PasswordHealthUiState.toneColor(): Color {
    if (isLoading) return MaterialTheme.colorScheme.surfaceContainerHigh
    return when (status) {
        PasswordHealthStatus.NO_DATA -> MaterialTheme.colorScheme.surfaceContainerHigh
        PasswordHealthStatus.ALL_GOOD -> MaterialTheme.colorScheme.secondaryContainer
        PasswordHealthStatus.NEEDS_ATTENTION -> MaterialTheme.colorScheme.errorContainer
    }
}

@Composable
@ReadOnlyComposable
internal fun PasswordHealthStatus.icon() = when (this) {
    PasswordHealthStatus.NO_DATA -> Icons.Default.HealthAndSafety
    PasswordHealthStatus.ALL_GOOD -> Icons.Default.GppGood
    PasswordHealthStatus.NEEDS_ATTENTION -> Icons.Default.GppBad
}

@Composable
@ReadOnlyComposable
internal fun PasswordHealthUiState.verdict(): String {
    if (isLoading) return stringResource(R.string.checking_passwords)
    return when (status) {
        PasswordHealthStatus.NO_DATA -> stringResource(R.string.password_health_no_data)
        PasswordHealthStatus.ALL_GOOD -> stringResource(R.string.password_health_all_good)
        PasswordHealthStatus.NEEDS_ATTENTION -> pluralStringResource(
            R.plurals.password_health_needs_attention,
            summary.needsAttention,
            summary.needsAttention
        )
    }
}

@Composable
@ReadOnlyComposable
internal fun PasswordHealthUiState.detailLine(): String? {
    if (isLoading) return null

    return when (status) {
        PasswordHealthStatus.NO_DATA -> null
        PasswordHealthStatus.ALL_GOOD -> pluralStringResource(
            R.plurals.detail_line_checked,
            totalPasswordCount,
            totalPasswordCount
        )

        PasswordHealthStatus.NEEDS_ATTENTION -> {
            listOfNotNull(
                countLabel(R.string.detail_line_checked_short, totalPasswordCount),
                countLabel(R.string.detail_line_breached, summary.breached),
                countLabel(R.string.detail_line_weak, summary.weak),
                countLabel(R.string.detail_line_reused, summary.reused),
                countLabel(R.string.detail_line_similar, summary.similar),
            ).joinToString(" \u2022 ")
        }
    }
}

@Composable
@ReadOnlyComposable
private fun countLabel(@StringRes id: Int, count: Int): String? =
    if (count > 0) stringResource(id, count, count) else null

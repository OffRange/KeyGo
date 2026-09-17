package de.davis.keygo.feature.password_health.presentation.model

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
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.feature.password_health.R

@Stable
internal sealed interface PasswordIssueType {
    data object Weak : PasswordIssueType
    data object Reused : PasswordIssueType
    data class Breached(val count: Int) : PasswordIssueType
}

@Stable
internal data class AttentionEntry(
    val id: ItemId,
    val title: String,
    val username: String?,
    val issueType: PasswordIssueType
)

internal enum class PasswordHealthStatus {
    NO_DATA,
    ALL_GOOD,
    NEEDS_ATTENTION
}

@Stable
internal data class PasswordHealthUiState(
    val isLoading: Boolean = false,
    val breachCheckEnabled: Boolean = false,
    val totalPasswordCount: Int = 0,
    val attentionEntries: List<AttentionEntry> = emptyList(),
    val generatePassword: Boolean = false,
) {
    val breachedCount = attentionEntries.count { it.issueType is PasswordIssueType.Breached }
    val weakCount = attentionEntries.count { it.issueType == PasswordIssueType.Weak }
    val reusedCount = attentionEntries.count { it.issueType == PasswordIssueType.Reused }

    val status = when {
        totalPasswordCount == 0 -> PasswordHealthStatus.NO_DATA
        attentionEntries.isNotEmpty() -> PasswordHealthStatus.NEEDS_ATTENTION
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
            attentionEntries.size,
            attentionEntries.size
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
            val checked = stringResource(R.string.detail_line_checked_short, totalPasswordCount)
            val breached =
                if (breachedCount > 0) stringResource(R.string.detail_line_breached, breachedCount)
                else null
            val weak =
                if (weakCount > 0) stringResource(R.string.detail_line_weak, weakCount)
                else null
            val reused =
                if (reusedCount > 0) stringResource(R.string.detail_line_reused, reusedCount)
                else null

            listOfNotNull(checked, breached, weak, reused).joinToString(" \u2022 ")
        }
    }
}

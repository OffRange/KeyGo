package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.model.RunPhase
import de.davis.keygo.feature.password_health.presentation.model.coverageNote
import de.davis.keygo.feature.password_health.presentation.model.icon
import de.davis.keygo.feature.password_health.presentation.model.statusCopy
import de.davis.keygo.feature.password_health.presentation.model.toneColor

@Composable
internal fun PasswordHealthStatus(
    state: PasswordHealthUiState,
    modifier: Modifier = Modifier
) {
    val containerColorTarget = state.toneColor()
    val containerColor by animateColorAsState(
        targetValue = containerColorTarget,
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = contentColorFor(containerColorTarget),
        label = "contentColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (state.isAccented) MaterialTheme.colorScheme.tertiary
        else contentColorFor(containerColorTarget),
        label = "iconColor"
    )
    val copy = state.statusCopy()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedContent(state.isFirstLoad) {
                    when (it) {
                        true -> CircularWavyProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )

                        false -> Icon(
                            imageVector = state.status.icon(),
                            contentDescription = null,
                            tint = iconColor,
                        )
                    }
                }

                Text(
                    text = copy.headline,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            SupportingLine(
                text = copy.verdict,
                label = "verdictLineVisibility",
                style = MaterialTheme.typography.bodyMedium,
            )

            AnimatedVisibility(
                visible = state.showsFindings,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                label = "breakdownVisibility"
            ) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeverityBreakdownBar(
                        breakdown = state.breakdown,
                        containerColor = containerColorTarget,
                        accentColor =
                            if (state.isAccented) MaterialTheme.colorScheme.tertiary
                            else containerColorTarget,
                    )

                    CountRow {
                        state.issueCounts.forEach {
                            CountText(label = it.label, count = it.count)
                        }
                    }
                }
            }

            SupportingLine(text = copy.detail, label = "detailLineVisibility")
            SupportingLine(text = state.coverageNote(), label = "coverageNoteVisibility")
            SupportingLine(text = state.breachGap?.message(), label = "breachGapVisibility")
        }
    }
}

@Composable
@ReadOnlyComposable
private fun CheckGap.message(): String? {
    val plural = when (reason) {
        // The check was disabled by the user, so there is nothing to warn about.
        GapReason.Disabled -> return null

        GapReason.Unreachable -> R.plurals.breach_check_unreachable
        GapReason.Failed -> R.plurals.breach_check_failed
    }

    return pluralStringResource(plural, unchecked.size, unchecked.size)
}

@Composable
private fun SupportingLine(
    text: String?,
    label: String,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        label = label
    ) {
        if (text != null) {
            Text(
                text = text,
                style = style,
            )
        }
    }
}

@Preview
@Composable
private fun AllGoodPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasswordHealthStatus(state = PasswordHealthUiState())
                PasswordHealthStatus(
                    state = PasswordHealthUiState(
                        phase = RunPhase.FirstLoad
                    )
                )
                PasswordHealthStatus(
                    state = PasswordHealthUiState(
                        totalPasswordCount = 12,
                    ),
                )
            }
        }
    }
}

@Preview
@Composable
private fun UnavailablePreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PasswordHealthStatus(
                    state = PasswordHealthUiState(
                        error = PasswordHealthReportError.Unreadable,
                    ),
                )
                PasswordHealthStatus(
                    state = PasswordHealthUiState(
                        totalPasswordCount = 9,
                        unreadable = setOf(newItemId(), newItemId(), newItemId()),
                    ),
                )
            }
        }
    }
}

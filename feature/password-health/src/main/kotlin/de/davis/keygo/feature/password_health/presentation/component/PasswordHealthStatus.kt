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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.presentation.model.AttentionEntry
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.model.PasswordIssueType
import de.davis.keygo.feature.password_health.presentation.model.detailLine
import de.davis.keygo.feature.password_health.presentation.model.icon
import de.davis.keygo.feature.password_health.presentation.model.toneColor
import de.davis.keygo.feature.password_health.presentation.model.verdict

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

    KeyGoCard(
        title = {
            Text(text = stringResource(R.string.password_health_overview_label))
        },
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        properties = KeyGoCardProperties.elevated(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        leadingItem = {
            AnimatedContent(state.isLoading) {
                when (it) {
                    true -> CircularWavyProgressIndicator(
                        modifier = Modifier.size(40.dp)
                    )

                    false -> Icon(
                        imageVector = state.status.icon(),
                        contentDescription = null,
                    )
                }
            }
        }
    ) {
        Text(text = state.verdict())

        val detail = state.detailLine()

        AnimatedVisibility(
            visible = detail != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            label = "detailLineVisibility"
        ) {
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                        isLoading = true
                    )
                )
                PasswordHealthStatus(
                    state = PasswordHealthUiState(
                        totalPasswordCount = 12,
                    )
                )
                PasswordHealthStatus(
                    state = PasswordHealthUiState(
                        totalPasswordCount = 12,
                        attentionEntries = listOf(
                            AttentionEntry(
                                id = newItemId(),
                                title = "Password #1",
                                username = null,
                                issueType = PasswordIssueType.Reused
                            ),
                            AttentionEntry(
                                id = newItemId(),
                                title = "Password #2",
                                username = null,
                                issueType = PasswordIssueType.Breached(count = 1)
                            ),
                            AttentionEntry(
                                id = newItemId(),
                                title = "Password #3",
                                username = null,
                                issueType = PasswordIssueType.Breached(count = 5)
                            ),
                            AttentionEntry(
                                id = newItemId(),
                                title = "Password #4",
                                username = null,
                                issueType = PasswordIssueType.Weak
                            ),
                        ),
                    ),
                )
            }
        }
    }
}

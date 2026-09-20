package de.davis.keygo.feature.password_health.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.ui.clipboard.setText
import de.davis.keygo.feature.item.create.presentation.password.GeneratePasswordModalBottomSheet
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.presentation.component.BreachCheck
import de.davis.keygo.feature.password_health.presentation.component.PasswordHealthStatus
import de.davis.keygo.feature.password_health.presentation.component.needsAttentionSection
import de.davis.keygo.feature.password_health.presentation.model.HealthSection
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthStatus
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.model.verdict
import kotlinx.coroutines.launch

@Composable
internal fun PasswordHealthContent(
    state: PasswordHealthUiState,
    onEvent: (PasswordHealthUiEvent) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedLabel = stringResource(R.string.fix_copied_label)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.password_health_title))
                },
                subtitle = {
                    Text(text = state.verdict(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(PasswordHealthUiEvent.RefreshHealthCheck) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item(key = "status") {
                    PasswordHealthStatus(state = state, modifier = Modifier.animateItem())
                }

                if (state.status == PasswordHealthStatus.NEEDS_ATTENTION) needsAttentionSection(
                    sections = state.healthSections,
                    pendingFix = state.pendingFix,
                    onEvent = onEvent,
                )

                item(key = "breach_check") {
                    Spacer(modifier = Modifier.height(28.dp))
                    BreachCheck(
                        state = state,
                        onChange = { onEvent(PasswordHealthUiEvent.OnBreachCheckChanged(it)) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    if (state.generatingFix != null) GeneratePasswordModalBottomSheet(
        onGenerated = { password ->
            scope.launch {
                clipboard.setText(label = copiedLabel, text = password, sensitive = true)
            }
            onEvent(PasswordHealthUiEvent.PasswordGenerated(password))
        },
        onDismiss = { onEvent(PasswordHealthUiEvent.DismissGeneratePassword) },
    )
}

@Preview
@Composable
private fun PasswordHealthContentPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            PasswordHealthContent(
                state = PasswordHealthUiState(
                    totalPasswordCount = 12,
                    reportedSections = listOf(
                        HealthSection(
                            severity = FindingSeverity.Medium,
                            groups = emptyList(),
                            standalone = listOf(
                                ItemHealth(
                                    itemId = newItemId(),
                                    title = "Weak password",
                                    username = null,
                                    issues = listOf(
                                        ItemIssue.Weak(score = PasswordScore.Weak)
                                    )
                                )
                            )
                        )
                    ),
                ),
                onEvent = {},
            )
        }
    }
}

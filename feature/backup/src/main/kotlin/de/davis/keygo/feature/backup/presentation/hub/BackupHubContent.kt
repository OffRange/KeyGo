package de.davis.keygo.feature.backup.presentation.hub

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupFailureReason
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.component.IconBadge
import de.davis.keygo.feature.backup.presentation.component.segmentContainerColor
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.hub.model.BackupGroup
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiState
import de.davis.keygo.feature.backup.presentation.hub.model.BackupSection

private const val DETAIL_SEPARATOR = " \u2022 "

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupHubContent(state: BackupHubUiState, onEvent: (BackupHubUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.dispatched_backups))
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HubActions(
                onExport = { onEvent(BackupHubUiEvent.OnScheduleBackupClick) },
                onImport = { onEvent(BackupHubUiEvent.OnRestoreBackup) },
            )

            AnimatedContent(
                targetState = state.hasItems,
                modifier = Modifier.weight(1f),
            ) { hasItems ->
                when {
                    hasItems -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        state.groups.forEach { group ->
                            stickyHeader(key = "header-${group.section}") {
                                BackupSectionHeader(group)
                            }
                            itemsIndexed(
                                items = group.items,
                                key = { _, item -> item.id },
                            ) { idx, item ->
                                DispatchedBackupRow(
                                    item = item,
                                    index = idx,
                                    count = group.items.size,
                                    onCancel = {
                                        onEvent(
                                            BackupHubUiEvent.OnCancelBackup(
                                                item.id,
                                                item.kind,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }

                    else -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_dispatched_backups),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubActions(onExport: () -> Unit, onImport: () -> Unit) {
    val buttonSize = ButtonDefaults.MediumContainerHeight
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val actionModifier = Modifier
            .heightIn(buttonSize)
            .weight(1f)
        val shapes = ButtonDefaults.shapesFor(buttonSize)
        val contentPadding = ButtonDefaults.contentPaddingFor(buttonSize)

        OutlinedButton(
            onClick = onExport,
            modifier = actionModifier,
            shapes = shapes,
            contentPadding = contentPadding,
        ) {
            HubActionLabel(
                buttonSize = buttonSize,
                icon = Icons.Default.Upload,
                label = stringResource(R.string.export_backup),
            )
        }
        Button(
            onClick = onImport,
            modifier = actionModifier,
            shapes = shapes,
            contentPadding = contentPadding,
        ) {
            HubActionLabel(
                buttonSize = buttonSize,
                icon = Icons.Default.Download,
                label = stringResource(R.string.import_backup),
            )
        }
    }
}

@Composable
private fun HubActionLabel(buttonSize: Dp, icon: ImageVector, label: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonSize)),
    )
    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(buttonSize)))
    Text(text = label, style = ButtonDefaults.textStyleFor(buttonSize))
}

@Composable
private fun BackupSectionHeader(group: BackupGroup) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(group.section.label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun DispatchedBackupRow(
    item: DispatchedBackup,
    index: Int,
    count: Int,
    onCancel: () -> Unit,
) {
    val statusColors = item.statusColors()
    val cancellable = item.state == DispatchedBackup.State.Enqueued ||
            item.state is DispatchedBackup.State.Running

    val defaultShapes =
        if (count == 1) ListItemDefaults.shapes(shape = MaterialTheme.shapes.large) else
            ListItemDefaults.shapes()
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count, defaultShapes),
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        leadingContent = {
            IconBadge(
                icon = item.icon,
                containerColor = statusColors.container,
                contentColor = statusColors.content,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val progress = (item.state as? DispatchedBackup.State.Running)?.progress
                if (progress != null) BackupProgress(progress = progress)
                else Text(text = item.detailText())

                item.failureReason?.let { FailureLine(item = item, reason = it) }
            }
        },
        trailingContent = {
            if (cancellable) IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel_backup),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        overlineContent = { Text(text = item.kind.label) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.destination.displayText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BackupProgress(progress: ExportProgress.InFlight) {
    when (progress) {
        is ExportProgress.Running -> {
            LinearProgressIndicator(
                progress = { progress.processed.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.backup_progress, progress.processed, progress.total),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        ExportProgress.Writing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

// A live schedule keeps its "Scheduled" pill even when its last run failed, so the reason needs
// framing there; in Recent the row already reads "Failed" and the bare reason is enough.
@Composable
private fun FailureLine(item: DispatchedBackup, reason: BackupFailureReason) {
    val reasonText = reason.label
    Text(
        text = if (item.section == BackupSection.Scheduled) stringResource(
            R.string.backup_failure_last_run,
            reasonText
        )
        else reasonText,
        style = MaterialTheme.typography.bodySmall,
        color = if (reason == BackupFailureReason.NothingToExport) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.error,
    )
}

/**
 * Parts joined by [DETAIL_SEPARATOR]: "Queued" and the format while it waits to dispatch, or the
 * format and "2 min ago" once it settles. "Queued" is scoped to In progress: a recurring job
 * sitting in Scheduled is waiting for its next period, not queued to run now.
 */
@Composable
private fun DispatchedBackup.detailText(): String {
    val queued = state == DispatchedBackup.State.Enqueued && section == BackupSection.InProgress
    return listOfNotNull(
        stringResource(R.string.backup_state_enqueued).takeIf { queued },
        format?.displayName,
        timestamp.takeIf { it > 0L && section == BackupSection.Recent }?.let { relativeTime(it) },
    ).joinToString(DETAIL_SEPARATOR)
}

private fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun BackupHubContentPreview() {
    KeyGoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BackupHubContent(
                state = BackupHubUiState(
                    groups = listOf(
                        DispatchedBackup(
                            id = "1",
                            kind = DispatchedBackup.Kind.Recurring,
                            state = DispatchedBackup.State.Running(ExportProgress.Running(2, 5)),
                            format = FileFormat.JSON,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.ThirdParty("Drive"),
                                displayPath = "Drive/Backups",
                            ),
                            timestamp = 4L,
                        ),
                        DispatchedBackup(
                            id = "1-",
                            kind = DispatchedBackup.Kind.OneTime,
                            state = DispatchedBackup.State.Enqueued,
                            format = FileFormat.JSON,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.ThirdParty("Nextcloud"),
                                displayPath = "Backups",
                            ),
                            timestamp = 4L,
                        ),
                        DispatchedBackup(
                            id = "2",
                            kind = DispatchedBackup.Kind.Recurring,
                            state = DispatchedBackup.State.Enqueued,
                            format = FileFormat.JSON,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.ThirdParty("Nextcloud"),
                                displayPath = "Nextcloud/KeyGo",
                            ),
                            timestamp = 3L,
                            failureReason = BackupFailureReason.WriteFailed,
                        ),
                        DispatchedBackup(
                            id = "3",
                            kind = DispatchedBackup.Kind.OneTime,
                            state = DispatchedBackup.State.Succeeded,
                            format = FileFormat.CSV,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.OnDevice,
                                displayPath = "Internal storage/Backups",
                            ),
                            timestamp = System.currentTimeMillis(),
                        ),
                        DispatchedBackup(
                            id = "4",
                            kind = DispatchedBackup.Kind.OneTime,
                            state = DispatchedBackup.State.Failed,
                            format = FileFormat.JSON,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.ThirdParty("Drive"),
                                displayPath = "Drive/Backups",
                            ),
                            timestamp = 1L,
                            failureReason = BackupFailureReason.WriteFailed,
                        ),
                        DispatchedBackup(
                            id = "5",
                            kind = DispatchedBackup.Kind.OneTime,
                            state = DispatchedBackup.State.Failed,
                            format = FileFormat.CSV,
                            destination = BackupDestination(
                                provider = BackupDestination.Provider.OnDevice,
                                displayPath = "Internal storage/Backups",
                            ),
                            timestamp = 2L,
                            failureReason = BackupFailureReason.NothingToExport,
                        ),
                    ).toGroups(),
                ),
                onEvent = {},
            )
        }
    }
}

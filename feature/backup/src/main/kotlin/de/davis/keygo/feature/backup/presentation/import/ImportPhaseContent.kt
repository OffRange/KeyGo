package de.davis.keygo.feature.backup.presentation.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportProgress
import de.davis.keygo.feature.backup.domain.model.ImportSummary
import de.davis.keygo.feature.backup.presentation.component.segmentContainerColor

@Composable
internal fun ImportRunningContent(
    progress: ImportProgress,
    modifier: Modifier = Modifier,
) {
    val label = when (progress) {
        ImportProgress.Reading -> stringResource(R.string.import_reading)
        ImportProgress.Parsing -> stringResource(R.string.import_parsing)
        else -> stringResource(R.string.import_running)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.import_running_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(
            modifier = Modifier.padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (progress is ImportProgress.Running && progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.processed.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.import_progress,
                        progress.processed,
                        progress.total,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                CircularProgressIndicator()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ImportResultContent(
    summary: ImportSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Default.TaskAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.import_result_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val rows = listOf(
                SummaryRowSpec(
                    icon = Icons.Default.CheckCircle,
                    label = stringResource(R.string.import_result_imported),
                    value = summary.imported,
                    iconTint = MaterialTheme.colorScheme.primary,
                ),
                SummaryRowSpec(
                    icon = Icons.Default.RemoveCircleOutline,
                    label = stringResource(R.string.import_result_skipped),
                    value = summary.skipped,
                ),
                // Only tinted as an error when something actually failed; a zero here is good news
                // and should not be shouted in red.
                SummaryRowSpec(
                    icon = Icons.Default.ErrorOutline,
                    label = stringResource(R.string.import_result_failed),
                    value = summary.failed,
                    iconTint = if (summary.failed > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                SummaryRowSpec(
                    icon = Icons.Default.Folder,
                    label = stringResource(R.string.import_result_vaults),
                    value = summary.vaultsCreated,
                ),
            )

            val neutralTint = MaterialTheme.colorScheme.onSurfaceVariant
            rows.forEachIndexed { position, row ->
                SummaryRow(
                    index = position,
                    count = rows.size,
                    icon = row.icon,
                    label = row.label,
                    value = row.value,
                    iconTint = row.iconTint ?: neutralTint,
                )
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.import_done))
        }
    }
}

/** A summary row before it knows its position in the group. A null tint means the neutral default. */
private class SummaryRowSpec(
    val icon: ImageVector,
    val label: String,
    val value: Int,
    val iconTint: Color? = null,
)

@Composable
private fun SummaryRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    label: String,
    value: Int,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        },
        trailingContent = {
            Text(text = value.toString(), style = MaterialTheme.typography.titleMedium)
        },
    ) {
        Text(text = label)
    }
}

@Composable
internal fun ImportErrorContent(
    error: ImportError,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (error) {
        ImportError.FileUnreadable -> stringResource(R.string.import_error_file_unreadable)
        ImportError.EmptyFile -> stringResource(R.string.import_error_empty)
        ImportError.NothingImported -> stringResource(R.string.import_error_nothing)
        ImportError.SessionLocked -> stringResource(R.string.import_error_session_locked)
        is ImportError.ParseFailed -> stringResource(R.string.import_error_parse)
        else -> stringResource(R.string.import_error_generic)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.import_error_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.import_try_again))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportResultContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ImportResultContent(
                summary = ImportSummary(
                    imported = 42,
                    skipped = 3,
                    failed = 1,
                    vaultsCreated = 1,
                ),
                onDone = {},
            )
        }
    }
}

package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.icon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectFileFormatContent(onEvent: (ExportWizardUiEvent) -> Unit) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            FileFormat.entries.forEachIndexed { index, type ->
                SegmentedListItem(
                    onClick = { onEvent(ExportWizardUiEvent.FileFormatSelected(type)) },
                    shapes = ListItemDefaults.segmentedShapes(index, FileFormat.entries.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ),
                    supportingContent = {
                        Text(text = type.description)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                        )
                    },
                    overlineContent = when {
                        type.recommented -> {
                            { Text(text = stringResource(R.string.recommented)) }
                        }

                        else -> null
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = type.displayName)
                }
            }
        }
    }
}

private val FileFormat.description
    @Composable
    get() = when (this) {
        FileFormat.JSON -> stringResource(R.string.export_description_json)
        FileFormat.CSV -> stringResource(R.string.export_description_csv)
    }
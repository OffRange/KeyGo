package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.presentation.description
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectCsvPresetContent(
    preset: CsvPreset,
    onEvent: (ExportWizardUiEvent) -> Unit,
) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            CsvPreset.entries.forEachIndexed { index, candidate ->
                SegmentedListItem(
                    onClick = { onEvent(ExportWizardUiEvent.CsvPresetSelected(candidate)) },
                    shapes = ListItemDefaults.segmentedShapes(index, CsvPreset.entries.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = if (preset == candidate)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = contentColorFor(
                            if (preset == candidate) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    ),
                    supportingContent = {
                        Text(text = candidate.description)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = candidate.displayName)
                }
            }
        }
    }
}

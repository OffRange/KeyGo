package de.davis.keygo.feature.backup.presentation.import.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.feature.backup.domain.model.CsvColumnAnalysis
import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.MappingConfidence

/**
 * One CSV column as the map-columns step edits it: what the analyzer found, plus the type currently
 * assigned to it.
 */
@Immutable
internal data class ColumnMappingRow(
    val index: Int,
    val header: String,
    val samples: List<String>,
    val suggestedType: CsvColumnType?,
    val confidence: MappingConfidence?,
    val selectedType: CsvColumnType?,
) {
    val needsVerification: Boolean = selectedType == suggestedType &&
            (confidence == MappingConfidence.Medium || confidence == MappingConfidence.Low)
}

internal fun CsvColumnAnalysis.toMappingRows(): List<ColumnMappingRow> = columns.map { column ->
    ColumnMappingRow(
        index = column.index,
        header = column.header,
        samples = column.samples,
        suggestedType = column.suggestedType,
        confidence = column.confidence,
        selectedType = column.suggestedType,
    )
}

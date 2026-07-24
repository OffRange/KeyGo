package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.feature.backup.domain.model.CsvColumnAnalysis
import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.DetectedColumn
import de.davis.keygo.feature.backup.domain.model.MappingConfidence
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.Confidence
import de.davisalessandro.keygo.rust.CsvAnalysis

/**
 * Fold the field-centric Rust analysis (`suggested`/`confidence` keyed by field) onto each
 * column: the greedy assignment guarantees at most one field points at any given column.
 */
internal fun CsvAnalysis.toDomain(): CsvColumnAnalysis = CsvColumnAnalysis(
    columns = columns.map { column ->
        val (type, confidence) = suggestionFor(column.index)
        DetectedColumn(
            index = column.index.toInt(),
            header = column.header,
            samples = column.sampleValues,
            suggestedType = type,
            confidence = confidence,
        )
    },
)

private fun CsvAnalysis.suggestionFor(index: UInt): Pair<CsvColumnType?, MappingConfidence?> = when (index) {
    suggested.title -> CsvColumnType.Title to confidence.title?.toDomain()
    suggested.url -> CsvColumnType.Url to confidence.url?.toDomain()
    suggested.username -> CsvColumnType.Username to confidence.username?.toDomain()
    suggested.password -> CsvColumnType.Password to confidence.password?.toDomain()
    suggested.notes -> CsvColumnType.Notes to confidence.notes?.toDomain()
    suggested.totp -> CsvColumnType.Totp to confidence.totp?.toDomain()
    else -> null to null
}

private fun Confidence.toDomain(): MappingConfidence = when (this) {
    Confidence.HIGH -> MappingConfidence.High
    Confidence.MEDIUM -> MappingConfidence.Medium
    Confidence.LOW -> MappingConfidence.Low
}

/** Turn a columnIndex -> type assignment into the Rust field-centric [ColumnMapping]. */
internal fun Map<Int, CsvColumnType?>.toColumnMapping(): ColumnMapping {
    val byType: Map<CsvColumnType, UInt> = entries
        .mapNotNull { (index, type) -> type?.let { it to index.toUInt() } }
        .toMap()
    return ColumnMapping(
        title = byType[CsvColumnType.Title],
        url = byType[CsvColumnType.Url],
        username = byType[CsvColumnType.Username],
        password = byType[CsvColumnType.Password],
        notes = byType[CsvColumnType.Notes],
        totp = byType[CsvColumnType.Totp],
    )
}

package de.davis.keygo.feature.backup.domain.model

data class DetectedColumn(
    val index: Int,
    val header: String,
    val samples: List<String>,
    val suggestedType: CsvColumnType?,
    val confidence: MappingConfidence?,
)

data class CsvColumnAnalysis(
    val columns: List<DetectedColumn>,
)

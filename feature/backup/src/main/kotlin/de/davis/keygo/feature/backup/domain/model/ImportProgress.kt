package de.davis.keygo.feature.backup.domain.model

sealed interface ImportProgress {
    data object Reading : ImportProgress
    data object Parsing : ImportProgress
    data class Running(val processed: Int, val total: Int) : ImportProgress
    data class Succeeded(val summary: ImportSummary) : ImportProgress
    data class Failed(val error: ImportError) : ImportProgress
}

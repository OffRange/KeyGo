package de.davis.keygo.feature.backup.domain.model

sealed interface ExportProgress {
    sealed interface InFlight : ExportProgress

    data class Running(val processed: Int, val total: Int) : InFlight
    data object Writing : InFlight
    data class Succeeded(val itemCount: Int) : ExportProgress
    data class Failed(val error: ExportError) : ExportProgress
}

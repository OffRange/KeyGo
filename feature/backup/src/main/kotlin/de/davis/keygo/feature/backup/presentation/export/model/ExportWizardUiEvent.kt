package de.davis.keygo.feature.backup.presentation.export.model

import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.model.IntervalUnit

internal sealed interface ExportWizardUiEvent {
    data object Back : ExportWizardUiEvent
    data object Continue : ExportWizardUiEvent
    data object ChooseDestination : ExportWizardUiEvent
    data class FileFormatSelected(val format: FileFormat) : ExportWizardUiEvent
    data class ScheduleModeSelected(val mode: ScheduleMode) : ExportWizardUiEvent
    data class IntervalUnitSelected(val unit: IntervalUnit) : ExportWizardUiEvent
    data class IntervalCountChanged(val count: Int) : ExportWizardUiEvent
    data class KeepCountChanged(val count: Int) : ExportWizardUiEvent
    data class KeepAllChanged(val keepAll: Boolean) : ExportWizardUiEvent
    data class EncryptionMethodSelected(val method: EncryptionMethod) : ExportWizardUiEvent
    data class CsvPresetSelected(val preset: CsvPreset) : ExportWizardUiEvent
}
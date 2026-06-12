package de.davis.keygo.feature.backup.presentation.export.model

import de.davis.keygo.feature.backup.domain.model.FileFormat

internal sealed interface ExportWizardUiEvent {
    data object Back : ExportWizardUiEvent
    data object Continue : ExportWizardUiEvent
    data class FileFormatSelected(val format: FileFormat) : ExportWizardUiEvent
}
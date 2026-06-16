package de.davis.keygo.feature.backup.presentation.export.model

internal sealed interface ExportWizardEvent {

    data object OpenDestinationPicker : ExportWizardEvent
}
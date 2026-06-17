package de.davis.keygo.feature.backup.presentation.export.model

internal sealed interface ExportWizardEvent {

    data object Finished : ExportWizardEvent
    data object PickFolder : ExportWizardEvent
    data class CreateFile(val suggestedName: String) : ExportWizardEvent
}

package de.davis.keygo.feature.backup.presentation.export.model

internal sealed interface ExportWizardEvent {

    /** Recurring backups: pick a folder the scheduler writes into over time. */
    data object PickFolder : ExportWizardEvent

    /** One-time backups: a "Save As" dialog seeded with [suggestedName]. */
    data class CreateFile(val suggestedName: String) : ExportWizardEvent
}

package de.davis.keygo.feature.backup.presentation.import.model

internal sealed interface ImportWizardEvent {
    data object PickFile : ImportWizardEvent
}

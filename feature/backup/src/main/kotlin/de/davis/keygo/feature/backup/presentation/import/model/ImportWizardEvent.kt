package de.davis.keygo.feature.backup.presentation.import.model

internal sealed interface ImportWizardEvent {
    data object PickFile : ImportWizardEvent

    /** The wizard has nothing left to go back to and hands control to whoever opened it. */
    data object Exit : ImportWizardEvent
}

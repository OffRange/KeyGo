package de.davis.keygo.feature.backup.domain.model

sealed interface FinishExportWizardError {
    data object PassphraseEmpty : FinishExportWizardError
    data object CryptoFailed : FinishExportWizardError
    data object SchedulePersistenceFailed : FinishExportWizardError
}

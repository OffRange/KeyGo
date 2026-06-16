package de.davis.keygo.feature.backup.presentation.export.model

import de.davis.keygo.feature.backup.domain.model.FileFormat

internal enum class ExportWizardStep {
    SelectFormat,
    Schedule,
    SelectDestination,
    ProvidePassphrase,
    Review,
}

internal fun exportStepsFor(format: FileFormat?): List<ExportWizardStep> =
    ExportWizardStep.entries.filter { step ->
        step != ExportWizardStep.ProvidePassphrase || (format?.encrypted ?: true)
    }
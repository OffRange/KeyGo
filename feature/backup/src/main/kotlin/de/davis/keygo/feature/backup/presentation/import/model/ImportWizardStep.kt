package de.davis.keygo.feature.backup.presentation.import.model

internal enum class ImportWizardStep {
    SelectFile,
    MapColumns,
    SelectVault,
    ProvidePassphrase,
}

internal fun importStepsFor(step: ImportWizardStep): List<ImportWizardStep> = when (step) {
    ImportWizardStep.SelectFile -> listOf(ImportWizardStep.SelectFile)
    ImportWizardStep.MapColumns -> listOf(
        ImportWizardStep.SelectFile,
        ImportWizardStep.MapColumns,
    )

    ImportWizardStep.SelectVault -> listOf(
        ImportWizardStep.SelectFile,
        ImportWizardStep.MapColumns,
        ImportWizardStep.SelectVault,
    )

    ImportWizardStep.ProvidePassphrase -> listOf(
        ImportWizardStep.SelectFile,
        ImportWizardStep.ProvidePassphrase,
    )
}

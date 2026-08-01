package de.davis.keygo.feature.backup.presentation.import.model

internal enum class ImportWizardStep {
    SelectFile,
    MapColumns,
    SelectVault,
    ProvidePassphrase,
}

private val CsvLane = listOf(
    ImportWizardStep.SelectFile,
    ImportWizardStep.MapColumns,
    ImportWizardStep.SelectVault,
)

private val JsonLane = listOf(
    ImportWizardStep.SelectFile,
    ImportWizardStep.ProvidePassphrase,
)

/** The steps walked so far on [step]'s lane, so the wizard can draw its progress. */
internal fun importStepsFor(step: ImportWizardStep): List<ImportWizardStep> {
    val lane = if (step == ImportWizardStep.ProvidePassphrase) JsonLane else CsvLane
    return lane.subList(0, lane.indexOf(step) + 1)
}

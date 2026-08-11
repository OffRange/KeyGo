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

/**
 * The whole lane [step] sits on, which is the single place the step order lives.
 *
 * [includeFileStep] is false when the host picked the file before the wizard opened. That step
 * never renders then, so it is not part of the walk at all.
 */
private fun laneFor(step: ImportWizardStep, includeFileStep: Boolean): List<ImportWizardStep> {
    val full = if (step == ImportWizardStep.ProvidePassphrase) JsonLane else CsvLane
    return if (includeFileStep) full else full - ImportWizardStep.SelectFile
}

/**
 * The steps walked so far on [step]'s lane, so the wizard can draw its progress. Dropping the file
 * step would otherwise leave the indicator permanently one dot ahead of the user.
 */
internal fun importStepsFor(
    step: ImportWizardStep,
    includeFileStep: Boolean = true,
): List<ImportWizardStep> = laneFor(step, includeFileStep)
    .let { it.subList(0, it.indexOf(step) + 1) }

/**
 * The step back from this one, or null when there is nothing left inside the wizard to go back to.
 *
 * [fileChosenByHost] collapses the file step: the host owns it, so backing out of the first step
 * the wizard actually renders means leaving the wizard rather than walking to
 * [ImportWizardStep.SelectFile].
 */
internal fun ImportWizardStep.previousStep(fileChosenByHost: Boolean): ImportWizardStep? =
    laneFor(this, includeFileStep = !fileChosenByHost)
        .let { it.getOrNull(it.indexOf(this) - 1) }

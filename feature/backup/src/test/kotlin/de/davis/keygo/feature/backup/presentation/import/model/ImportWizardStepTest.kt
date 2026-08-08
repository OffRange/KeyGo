package de.davis.keygo.feature.backup.presentation.import.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportWizardStepTest {

    @Test
    fun `the file step alone is the whole lane when the wizard owns it`() {
        assertEquals(
            listOf(ImportWizardStep.SelectFile),
            importStepsFor(ImportWizardStep.SelectFile),
        )
    }

    @Test
    fun `csv lane walks the file step first`() {
        assertEquals(
            listOf(ImportWizardStep.SelectFile, ImportWizardStep.MapColumns),
            importStepsFor(ImportWizardStep.MapColumns),
        )
    }

    @Test
    fun `csv lane walks all three steps by the time it reaches select vault`() {
        assertEquals(
            listOf(
                ImportWizardStep.SelectFile,
                ImportWizardStep.MapColumns,
                ImportWizardStep.SelectVault,
            ),
            importStepsFor(ImportWizardStep.SelectVault),
        )
    }

    @Test
    fun `json lane walks the file step first`() {
        assertEquals(
            listOf(ImportWizardStep.SelectFile, ImportWizardStep.ProvidePassphrase),
            importStepsFor(ImportWizardStep.ProvidePassphrase),
        )
    }

    @Test
    fun `csv lane drops the file step when the host owns it`() {
        assertEquals(
            listOf(ImportWizardStep.MapColumns, ImportWizardStep.SelectVault),
            importStepsFor(ImportWizardStep.SelectVault, includeFileStep = false),
        )
    }

    @Test
    fun `json lane drops the file step when the host owns it`() {
        assertEquals(
            listOf(ImportWizardStep.ProvidePassphrase),
            importStepsFor(ImportWizardStep.ProvidePassphrase, includeFileStep = false),
        )
    }

    @Test
    fun `a host owned lane standing on the file step has walked nothing`() {
        assertEquals(
            emptyList(),
            importStepsFor(ImportWizardStep.SelectFile, includeFileStep = false),
        )
    }

    @Test
    fun `map columns goes back to the file step when the wizard owns it`() {
        assertEquals(
            ImportWizardStep.SelectFile,
            ImportWizardStep.MapColumns.previousStep(fileChosenByHost = false),
        )
    }

    @Test
    fun `map columns hands back to the host when the host owns the file`() {
        assertNull(ImportWizardStep.MapColumns.previousStep(fileChosenByHost = true))
    }

    @Test
    fun `provide passphrase goes back to the file step when the wizard owns it`() {
        assertEquals(
            ImportWizardStep.SelectFile,
            ImportWizardStep.ProvidePassphrase.previousStep(fileChosenByHost = false),
        )
    }

    @Test
    fun `provide passphrase hands back to the host when the host owns the file`() {
        assertNull(ImportWizardStep.ProvidePassphrase.previousStep(fileChosenByHost = true))
    }

    @Test
    fun `select vault always goes back to map columns`() {
        assertEquals(
            ImportWizardStep.MapColumns,
            ImportWizardStep.SelectVault.previousStep(fileChosenByHost = true),
        )
        assertEquals(
            ImportWizardStep.MapColumns,
            ImportWizardStep.SelectVault.previousStep(fileChosenByHost = false),
        )
    }

    @Test
    fun `the file step has nothing to go back to`() {
        assertNull(ImportWizardStep.SelectFile.previousStep(fileChosenByHost = false))
    }

}

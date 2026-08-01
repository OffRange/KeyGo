package de.davis.keygo.feature.backup.presentation.import.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportWizardStepTest {

    @Test
    fun `SelectFile shows a single step`() {
        assertEquals(
            listOf(ImportWizardStep.SelectFile),
            importStepsFor(ImportWizardStep.SelectFile),
        )
    }

    @Test
    fun `ProvidePassphrase reveals the passphrase step`() {
        assertEquals(
            listOf(ImportWizardStep.SelectFile, ImportWizardStep.ProvidePassphrase),
            importStepsFor(ImportWizardStep.ProvidePassphrase),
        )
    }

    @Test
    fun `SelectVault reveals the file, mapping and vault steps`() {
        assertEquals(
            listOf(
                ImportWizardStep.SelectFile,
                ImportWizardStep.MapColumns,
                ImportWizardStep.SelectVault,
            ),
            importStepsFor(ImportWizardStep.SelectVault),
        )
    }
}

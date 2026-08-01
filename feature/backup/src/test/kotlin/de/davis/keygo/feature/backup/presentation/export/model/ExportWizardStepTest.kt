package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.foundation.text.input.TextFieldState
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.FileFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportWizardStepTest {

    @Test
    fun `json flow shows the passphrase step and skips the csv preset step`() {
        assertEquals(
            listOf(
                ExportWizardStep.SelectFormat,
                ExportWizardStep.Schedule,
                ExportWizardStep.SelectDestination,
                ExportWizardStep.ProvidePassphrase,
                ExportWizardStep.Review,
            ),
            exportStepsFor(FileFormat.JSON),
        )
    }

    @Test
    fun `csv flow shows the preset step and skips the passphrase step`() {
        assertEquals(
            listOf(
                ExportWizardStep.SelectFormat,
                ExportWizardStep.Schedule,
                ExportWizardStep.SelectDestination,
                ExportWizardStep.SelectCsvPreset,
                ExportWizardStep.Review,
            ),
            exportStepsFor(FileFormat.CSV),
        )
    }

    @Test
    fun `unknown format keeps the passphrase step and no preset step`() {
        assertEquals(
            listOf(
                ExportWizardStep.SelectFormat,
                ExportWizardStep.Schedule,
                ExportWizardStep.SelectDestination,
                ExportWizardStep.ProvidePassphrase,
                ExportWizardStep.Review,
            ),
            exportStepsFor(null),
        )
    }

    @Test
    fun `ark method makes the passphrase step valid without a passphrase`() {
        val state = ExportWizardUiState(
            formatState = SelectFormatState(format = FileFormat.JSON),
            scheduleState = SelectScheduleState(),
            destinationState = SelectDestinationState(),
            providePassphraseState = ProvidePassphraseState(
                passphraseTextFieldState = TextFieldState(),
                confirmPassphraseTextFieldState = TextFieldState(),
                method = EncryptionMethod.Ark,
            ),
            step = ExportWizardStep.ProvidePassphrase,
        )

        assertTrue(state.canContinue)
    }
}

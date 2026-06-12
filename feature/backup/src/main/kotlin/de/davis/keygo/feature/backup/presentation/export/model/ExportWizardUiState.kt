package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.backup.domain.model.FileFormat

@Stable
internal data class ExportWizardUiState(
    val formatState: SelectFormatState,
    val providePassphraseState: ProvidePassphraseState,
    val step: ExportWizardStep = ExportWizardStep.SelectFormat,
)

@Stable
internal data class SelectFormatState(
    val format: FileFormat? = null
)

@Stable
internal data class ProvidePassphraseState(
    val passphraseTextFieldState: TextFieldState,
    val confirmPassphraseTextFieldState: TextFieldState,
    val passphraseScore: PasswordScore = PasswordScore.None,
)

internal enum class ExportWizardStep {
    SelectFormat,
    ProvidePassphrase
}
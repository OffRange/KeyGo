package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.FileFormat

@Stable
internal data class ExportWizardUiState(
    val formatState: SelectFormatState,
    val scheduleState: SelectScheduleState,
    val destinationState: SelectDestinationState,
    val providePassphraseState: ProvidePassphraseState,
    val step: ExportWizardStep = ExportWizardStep.SelectFormat,
)

@Stable
internal data class SelectFormatState(
    val format: FileFormat? = null
)

@Stable
internal data class SelectDestinationState(
    val destination: BackupDestination? = null,
)

@Stable
internal data class BackupDestination(
    val providerLabel: String,
    val displayPath: String,
)

@Stable
internal data class SelectScheduleState(
    val mode: ScheduleMode = ScheduleMode.OneTime,
    val interval: BackupInterval = BackupInterval.Day(1),
    val keepCount: Int = 5,
    val keepAll: Boolean = false,
)

internal enum class ScheduleMode {
    OneTime,
    Recurring,
}

internal enum class IntervalUnit {
    Days,
    Weeks,
}

@Stable
internal data class ProvidePassphraseState(
    val passphraseTextFieldState: TextFieldState,
    val confirmPassphraseTextFieldState: TextFieldState,
    val passphraseScore: PasswordScore = PasswordScore.None,
)

internal enum class ExportWizardStep {
    SelectFormat,
    Schedule,
    SelectDestination,
    ProvidePassphrase,
    Review,
}
package de.davis.keygo.feature.settings.presentation

internal sealed interface SettingsUiEvent {
    data class SetBiometrics(val enabled: Boolean) : SettingsUiEvent
    data class SetAutofill(val enabledRequest: Boolean) : SettingsUiEvent
    data object ResetPassword : SettingsUiEvent
    data object ExportData : SettingsUiEvent
    data object ImportData : SettingsUiEvent
    data object ReportIssue : SettingsUiEvent
    data object LibrariesClicked : SettingsUiEvent
}

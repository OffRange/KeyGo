package de.davis.keygo.feature.settings.presentation

internal sealed interface SettingsUiEvent {
    data class SetBiometrics(val enabled: Boolean) : SettingsUiEvent
    data class SetAutofill(val enabledRequest: Boolean) : SettingsUiEvent
    data object OpenChromeAutofillSettings : SettingsUiEvent
    data object ResetPassword : SettingsUiEvent
    data object OpenBackup : SettingsUiEvent
    data object ReportIssue : SettingsUiEvent
    data object LibrariesClicked : SettingsUiEvent
}

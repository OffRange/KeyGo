package de.davis.keygo.feature.settings.presentation

internal data class SettingsUiState(
    val autofillEnabled: Boolean = false,
    val chromeAutofillEnabled: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val biometricsEnabled: Boolean = false,
    val version: String = "2.0.0",
    /** When the newest successful backup finished, or `null` while none has. */
    val lastBackupAt: Long? = null,
)

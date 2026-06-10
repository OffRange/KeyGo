package de.davis.keygo.feature.settings.presentation

internal data class SettingsUiState(
    val autofillEnabled: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val biometricsEnabled: Boolean = false,
    val version: String = "2.0.0",
)

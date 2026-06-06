package de.davis.keygo.feature.settings.presentation

internal sealed interface SettingsEvent {

    data object OpenAutofillSelection : SettingsEvent
    data class EnableBiometric(val enable: Boolean) : SettingsEvent
}
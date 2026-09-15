package de.davis.keygo.feature.settings.presentation

import de.davis.keygo.core.security.domain.model.LockInfo

internal data class SettingsUiState(
    val autofillEnabled: Boolean = false,
    val chromeAutofillEnabled: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val biometricsEnabled: Boolean = false,
    /** An enrollment or removal is running, so the toggle takes no further input until it ends. */
    val biometricsUpdating: Boolean = false,
    val version: String = "2.0.0",
    /** When the newest successful backup finished, or `null` while none has. */
    val lastBackupAt: Long? = null,
    val lockTimeout: LockInfo.Timeout = LockInfo.Timeout.IMMEDIATELY,
)

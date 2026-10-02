package de.davis.keygo.feature.password_health.domain.model

sealed interface PasswordHealthReportError {
    data object NoPasswords : PasswordHealthReportError
    data object Unreadable : PasswordHealthReportError
}

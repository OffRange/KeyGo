package de.davis.keygo.feature.password_health.domain.model

sealed interface HealthReportStoreError {
    object NoReportStored : HealthReportStoreError
    object CryptoFailed : HealthReportStoreError
    object CorruptedReport : HealthReportStoreError
}

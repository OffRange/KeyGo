package de.davis.keygo.feature.password_health.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.password_health.domain.model.HealthReportStoreError
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport

interface HealthReportStoreRepository {

    suspend fun storeReport(report: StoredHealthReport): Result<Unit, HealthReportStoreError>
    suspend fun load(): Result<StoredHealthReport, HealthReportStoreError>
}

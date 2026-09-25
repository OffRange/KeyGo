package de.davis.keygo.feature.password_health.data.repository

import androidx.datastore.core.DataStore
import com.google.protobuf.kotlin.isNotEmpty
import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthReportStore
import de.davis.keygo.feature.password_health.data.mapper.toCompressedByteArray
import de.davis.keygo.feature.password_health.data.mapper.toStoredHealthReport
import de.davis.keygo.feature.password_health.di.annotation.HealthReportStore
import de.davis.keygo.feature.password_health.domain.model.HealthReportStoreError
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import de.davis.keygo.feature.password_health.domain.repository.HealthReportStoreRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import java.util.UUID

@Single
internal class HealthReportStoreRepositoryImpl(
    @HealthReportStore
    private val dataStore: DataStore<ProtoHealthReportStore>,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
) : HealthReportStoreRepository {

    override suspend fun storeReport(report: StoredHealthReport): Result<Unit, HealthReportStoreError> =
        resultBinding {
            val stored = dataStore.data.first()
                .takeIf { it.wrappedKey.isNotEmpty() }

            val compressedReport = report.toCompressedByteArray()

            val (sealedReport, key) = cryptographicScopeProvider.accountScope(
                namespace = REPORT_NAMESPACE,
                wrapped = stored?.toWrappedKey(),
            ) {
                compressedReport.encrypt(REPORT_LABEL) to wrapCurrentItemKey()
            }.bind { HealthReportStoreError.CryptoFailed }

            dataStore.updateData {
                it.toBuilder()
                    .setWrappedKey(key.wrappedKey.toByteString())
                    .setWrappedKeyNonce(key.keyNonce.toByteString())
                    .setCiphertext(sealedReport.data.toByteString())
                    .setIv(sealedReport.iv.toByteString())
                    .build()
            }
        }

    override suspend fun load(): Result<StoredHealthReport, HealthReportStoreError> =
        resultBinding {
            val stored = dataStore.data.first()
            if (stored.wrappedKey.isEmpty || stored.ciphertext.isEmpty) {
                return Result.Failure(HealthReportStoreError.NoReportStored)
            }

            val compressedReport = cryptographicScopeProvider.accountScope(
                namespace = REPORT_NAMESPACE,
                wrapped = stored.toWrappedKey(),
            ) {
                CryptographicData(
                    data = stored.ciphertext.toByteArray(),
                    iv = stored.iv.toByteArray(),
                ).decrypt(REPORT_LABEL)
            }.bind { HealthReportStoreError.CryptoFailed }

            compressedReport.toStoredHealthReport()
                .asResult(HealthReportStoreError.CorruptedReport)
                .bind()
        }

    private fun ProtoHealthReportStore.toWrappedKey() = KeyInformation(
        wrappedKey = wrappedKey.toByteArray(),
        keyNonce = wrappedKeyNonce.toByteArray(),
    )

    companion object {
        // On-disk identity: the AAD domain separator for every stored report. Never change it;
        // doing so makes every previously stored report permanently undecryptable.
        private val REPORT_NAMESPACE: UUID = UUID.fromString("39050172-ce6d-4f4d-8787-6e12ab44eb0e")

        private const val REPORT_LABEL = "password_health_report"
    }
}

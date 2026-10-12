package de.davis.keygo.feature.password_health.data.repository

import androidx.datastore.core.DataStore
import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.SecretKind
import de.davis.keygo.core.security.domain.crypto.AccountRecordSealer
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.SealedAccountRecord
import de.davis.keygo.core.security.domain.model.AccountRecordError
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
    private val accountRecordSealer: AccountRecordSealer,
) : HealthReportStoreRepository {

    override suspend fun storeReport(report: StoredHealthReport): Result<Unit, HealthReportStoreError> =
        resultBinding {
            val sealed = accountRecordSealer.seal(
                namespace = REPORT_NAMESPACE,
                kind = SecretKind.PasswordHealthReport,
                plaintext = report.toCompressedByteArray(),
            ).bind { HealthReportStoreError.CryptoFailed }

            dataStore.updateData {
                it.toBuilder()
                    .setWrappedKey(sealed.wrappedKey.wrappedKey.toByteString())
                    .setWrappedKeyNonce(sealed.wrappedKey.keyNonce.toByteString())
                    .setCiphertext(sealed.data.data.toByteString())
                    .setIv(sealed.data.iv.toByteString())
                    .build()
            }
        }

    override suspend fun load(): Result<StoredHealthReport, HealthReportStoreError> =
        resultBinding {
            val stored = dataStore.data.first()
            if (stored.wrappedKey.isEmpty || stored.ciphertext.isEmpty) {
                return Result.Failure(HealthReportStoreError.NoReportStored)
            }

            val compressedReport = accountRecordSealer.open(
                namespace = REPORT_NAMESPACE,
                kind = SecretKind.PasswordHealthReport,
                record = stored.toSealedRecord(),
            ).bind {
                if (it == AccountRecordError.Corrupted) HealthReportStoreError.CorruptedReport
                else HealthReportStoreError.CryptoFailed
            }

            compressedReport.toStoredHealthReport()
                .asResult(HealthReportStoreError.CorruptedReport)
                .bind()
        }

    private fun ProtoHealthReportStore.toSealedRecord() = SealedAccountRecord(
        wrappedKey = KeyInformation(
            wrappedKey = wrappedKey.toByteArray(),
            keyNonce = wrappedKeyNonce.toByteArray(),
        ),
        data = CryptographicData(data = ciphertext.toByteArray(), iv = iv.toByteArray()),
    )

    companion object {
        // On-disk identity: part of every stored report's AAD. Changing it makes stored reports
        // unreadable, which costs only a rescan: the store is a cache.
        private val REPORT_NAMESPACE: UUID = UUID.fromString("39050172-ce6d-4f4d-8787-6e12ab44eb0e")

    }
}

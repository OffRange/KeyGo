package de.davis.keygo.feature.backup.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJob
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJobKt
import de.davis.keygo.feature.backup.data.local.model.protoBackupJob
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupFailureReason
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.FileFormat

internal fun ProtoBackupJob.toDomain(): BackupJob {
    val fileFormat = FileFormat.valueOf(format)
    return BackupJob(
        uri = BackupDestinationUri(uri),
        format = fileFormat,
        wrappedPassphrase = if (hasPassphraseCt() && hasPassphraseIv())
            CryptographicData(
                data = passphraseCt.toByteArray(),
                iv = passphraseIv.toByteArray(),
            )
        else null,
        encryption = if (fileFormat.encrypted)
            (if (hasEncryption()) runCatching { EncryptionMethod.valueOf(encryption) }.getOrNull() else null)
                ?: EncryptionMethod.Passphrase
        else null,
        csvPreset = if (fileFormat == FileFormat.CSV)
            (if (hasCsvPreset()) runCatching { CsvPreset.valueOf(csvPreset) }.getOrNull() else null)
                ?: CsvPreset.Browser
        else null,
        keepCount = if (hasKeepCount()) keepCount else null,
        createdAt = createdAt,
        finishedAt = if (hasFinishedAt()) finishedAt else null,
        lastResult = backupResultFromProto(
            resultName = if (hasLastResult()) lastResult else null,
            errorName = if (hasLastError()) lastError else null,
        ),
        cancelled = this.cancelled,
        destinationName = if (hasDestinationName()) destinationName else null,
    )
}

internal fun BackupJob.toProto() = protoBackupJob {
    uri = this@toProto.uri.value
    format = this@toProto.format.name
    wrappedPassphrase?.let {
        passphraseCt = it.data.toByteString()
        passphraseIv = it.iv.toByteString()
    }
    this@toProto.encryption?.let { encryption = it.name }
    this@toProto.csvPreset?.let { csvPreset = it.name }
    this@toProto.keepCount?.let { keepCount = it }
    createdAt = this@toProto.createdAt
    this@toProto.finishedAt?.let { finishedAt = it }
    this@toProto.lastResult?.let { writeResult(it) }
    cancelled = this@toProto.cancelled
    this@toProto.destinationName?.let { destinationName = it }
}

// Encodes a result into the proto's two independent fields. The recurring record is reused every
// run, so a stale reason must not outlive its run - clear it when the new result carries none.
internal fun ProtoBackupJobKt.Dsl.writeResult(result: BackupResult) {
    lastResult = result.protoResultName
    val error = result.protoErrorName
    if (error != null) lastError = error
    else clearLastError()
}

// The proto persists the outcome as two independent strings (result + reason). Keep that layout so
// no DataStore migration is needed, but resolve them into a single sealed BackupResult in the domain.
private const val PROTO_RESULT_SUCCESS = "Success"
private const val PROTO_RESULT_FAILURE = "Failure"

internal val BackupResult.protoResultName: String
    get() = when (this) {
        BackupResult.Success -> PROTO_RESULT_SUCCESS
        is BackupResult.Failure -> PROTO_RESULT_FAILURE
    }

internal val BackupResult.protoErrorName: String?
    get() = (this as? BackupResult.Failure)?.reason?.name

private fun backupResultFromProto(resultName: String?, errorName: String?): BackupResult? =
    when (resultName) {
        PROTO_RESULT_SUCCESS -> BackupResult.Success
        PROTO_RESULT_FAILURE -> BackupResult.Failure(
            errorName?.let { runCatching { BackupFailureReason.valueOf(it) }.getOrNull() },
        )

        else -> null
    }

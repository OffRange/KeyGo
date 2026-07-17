package de.davis.keygo.feature.backup.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJob
import de.davis.keygo.feature.backup.data.local.model.protoBackupJob
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
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
        lastResult = if (hasLastResult())
            runCatching { BackupResult.valueOf(lastResult) }.getOrNull()
        else null,
        cancelled = this.cancelled,
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
    this@toProto.lastResult?.let { lastResult = it.name }
    cancelled = this@toProto.cancelled
}

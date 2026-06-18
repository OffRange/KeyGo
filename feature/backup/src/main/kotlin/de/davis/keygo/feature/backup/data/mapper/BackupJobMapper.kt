package de.davis.keygo.feature.backup.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupJob
import de.davis.keygo.feature.backup.data.local.model.protoBackupJob
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.FileFormat

internal fun ProtoBackupJob.toDomain() = BackupJob(
    uri = BackupDestinationUri(uri),
    format = FileFormat.valueOf(this@toDomain.format),
    passphrase = if (hasPassphraseCt() && hasPassphraseIv())
        CryptographicData(
            data = passphraseCt.toByteArray(),
            iv = passphraseIv.toByteArray()
        )
    else null
)

internal fun BackupJob.toProto() = protoBackupJob {
    uri = this@toProto.uri.value
    format = this@toProto.format.name
    passphrase?.let {
        passphraseCt = it.data.toByteString()
        passphraseIv = it.iv.toByteString()
    }
}

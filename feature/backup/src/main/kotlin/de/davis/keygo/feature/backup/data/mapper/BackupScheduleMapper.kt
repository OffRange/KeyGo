package de.davis.keygo.feature.backup.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.feature.backup.data.local.model.ProtoBackupSchedule
import de.davis.keygo.feature.backup.data.local.model.protoBackupSchedule
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupSchedule
import de.davis.keygo.feature.backup.domain.model.FileFormat

internal fun ProtoBackupSchedule.toDomain() = BackupSchedule(
    uri = BackupDestinationUri(uri),
    format = FileFormat.valueOf(this@toDomain.format),
    passphrase = CryptographicData(
        data = passphraseCipherText.toByteArray(),
        iv = passphraseIV.toByteArray()
    ),
)

internal fun BackupSchedule.toProto() = protoBackupSchedule {
    uri = this@toProto.uri.value
    format = this@toProto.format.name
    passphraseCipherText = passphrase.data.toByteString()
    passphraseIV = passphrase.iv.toByteString()
}

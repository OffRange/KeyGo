package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davisalessandro.keygo.rust.BackupException

internal fun BackupException.toExportError(): ExportError = when (this) {
    is BackupException.Locked -> ExportError.SessionLocked
    else -> ExportError.SerializationFailed(this)
}

internal fun CryptoScopeError.toExportError(): ExportError =
    if (this == CryptoScopeError.NoActiveSession) ExportError.SessionLocked
    else ExportError.CryptoFailed

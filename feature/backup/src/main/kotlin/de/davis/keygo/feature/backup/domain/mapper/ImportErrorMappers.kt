package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davisalessandro.keygo.rust.BackupException

internal fun BackupException.toImportError(): ImportError = when (this) {
    is BackupException.Crypto,
    is BackupException.CredentialMismatch -> ImportError.WrongCredential

    else -> ImportError.ParseFailed(this)
}

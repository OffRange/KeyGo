package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davisalessandro.keygo.rust.BackupException

/**
 * The [BackupException.Locked] arm is not redundant with the `isActive` guards in
 * `ImportBackupUseCase`. Those guards run before the call into Rust; auto-lock can fire between a
 * guard and the call it protects, and without this arm the user is told the file failed to parse
 * when the real cause is that their session ended.
 */
internal fun BackupException.toImportError(): ImportError = when (this) {
    is BackupException.Locked -> ImportError.SessionLocked

    is BackupException.Crypto,
    is BackupException.CredentialMismatch -> ImportError.WrongCredential

    else -> ImportError.ParseFailed(this)
}

package de.davis.keygo.feature.backup.domain.model

import de.davisalessandro.keygo.rust.BackupException

sealed interface ImportError {
    data object SessionLocked : ImportError
    data object FileUnreadable : ImportError
    data object EmptyFile : ImportError
    data object WrongCredential : ImportError
    data object PassphraseRequired : ImportError
    data class ParseFailed(val cause: BackupException) : ImportError
    data object NothingImported : ImportError
}

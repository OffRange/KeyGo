package de.davis.passwordmanager.backup

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class BackupOperation : Parcelable {
    IMPORT,
    EXPORT
}
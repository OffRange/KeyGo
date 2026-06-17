package de.davis.keygo.feature.backup.data

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import de.davis.keygo.feature.backup.domain.PersistableUriManager
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import org.koin.core.annotation.Single

@Single
class PersistableUriManagerImpl(
    private val context: Context
) : PersistableUriManager {

    override fun takePersistableUriPermission(uri: BackupDestinationUri) {
        context.contentResolver.takePersistableUriPermission(
            uri.value.toUri(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    override fun releasePersistableUriPermission(uri: BackupDestinationUri) {
        context.contentResolver.releasePersistableUriPermission(
            uri.value.toUri(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}
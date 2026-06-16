package de.davis.keygo.feature.backup.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import de.davis.keygo.feature.backup.domain.BackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
internal class BackupDestinationResolverImpl(
    private val context: Context
) : BackupDestinationResolver {

    override suspend fun resolve(
        uri: BackupDestinationUri
    ): BackupDestination = withContext(Dispatchers.IO) {
        val uri = uri.value.toUri()

        BackupDestination(
            provider = uri.providerLabel(),
            displayPath = uri.displayName()
        )
    }

    private fun Uri.providerLabel(): BackupDestination.Provider {
        val authority = authority ?: return BackupDestination.Provider.Unknown

        if (authority == EXTERNAL_STORAGE) return BackupDestination.Provider.OnDevice

        val pm = context.packageManager
        val info =
            pm.resolveContentProvider(authority, 0)
                ?: return BackupDestination.Provider.ThirdParty(authority)

        val label = pm.getApplicationLabel(info.applicationInfo).toString()
        return BackupDestination.Provider.ThirdParty(label)
    }

    fun Uri.displayName(): String {
        val docId = DocumentsContract.getTreeDocumentId(this)


        return if (authority == EXTERNAL_STORAGE) {
            val (volume, path) = docId.split(":", limit = 2)
                .let { it[0] to it.getOrElse(1) { "" } }

            val root = if (volume == "primary") "Internal storage" else volume
            if (path.isBlank()) root else "$root/$path"
        } else {
            queryDisplayName() ?: DocumentsContract.getTreeDocumentId(this)
        }
    }

    private fun Uri.queryDisplayName(): String? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            this,
            DocumentsContract.getTreeDocumentId(this),
        )
        return context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    companion object {
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    }
}
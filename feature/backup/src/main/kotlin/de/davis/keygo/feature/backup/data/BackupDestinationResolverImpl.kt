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
    private val context: Context,
) : BackupDestinationResolver {

    override suspend fun resolve(
        uri: BackupDestinationUri,
        cachedName: String?,
    ): BackupDestination = withContext(Dispatchers.IO) {
        val parsed = uri.value.toUri()

        if (DocumentsContract.isTreeUri(parsed)) parsed.asFolderDestination(cachedName)
        else parsed.asFileDestination(cachedName)
    }

    // The provider is only ever asked for the one part it alone knows - a folder's name, or a
    // file's. The provider label and the on-device path are derived locally, so they stay correct
    // whether or not a grant is still behind the uri.
    private fun Uri.asFolderDestination(cachedName: String?) = BackupDestination(
        provider = providerLabel(),
        displayPath = cachedName ?: treeDisplayPath(),
        fileName = null,
    )

    private fun Uri.asFileDestination(cachedName: String?) = BackupDestination(
        provider = providerLabel(),
        displayPath = documentDisplayPath(),
        fileName = cachedName ?: queryDisplayName(),
    )

    private fun Uri.providerLabel(): BackupDestination.Provider {
        val authority = authority ?: return BackupDestination.Provider.Unknown

        if (authority == EXTERNAL_STORAGE) return BackupDestination.Provider.OnDevice

        val pm = context.packageManager
        val info = pm.resolveContentProvider(authority, 0)
            ?: return BackupDestination.Provider.ThirdParty(authority)

        val label = pm.getApplicationLabel(info.applicationInfo).toString()
        return BackupDestination.Provider.ThirdParty(label)
    }

    private fun Uri.treeDisplayPath(): String {
        val docId = DocumentsContract.getTreeDocumentId(this)
        return if (authority == EXTERNAL_STORAGE) externalStoragePath(docId)
        else queryTreeDisplayName() ?: docId
    }

    private fun Uri.documentDisplayPath(): String {
        if (authority != EXTERNAL_STORAGE)
            return (providerLabel() as? BackupDestination.Provider.ThirdParty)?.name ?: ""

        // Strip the file name: the card shows it separately via fileName. A
        // third-party provider that doesn't answer the display-name query falls
        // back to its app label (or "" for an unresolvable authority) - a cosmetic
        // gap, never a crash, in an error path CreateDocument shouldn't reach.
        val docId = DocumentsContract.getDocumentId(this)
        val (volume, path) = docId.splitVolumeAndPath()
        val root = if (volume == "primary") "Internal storage" else volume
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        return listOf(root, parent).filter { it.isNotBlank() }.joinToString("/")
    }

    private fun externalStoragePath(docId: String): String {
        val (volume, path) = docId.splitVolumeAndPath()
        val root = if (volume == "primary") "Internal storage" else volume
        return if (path.isBlank()) root else "$root/$path"
    }

    private fun String.splitVolumeAndPath(): Pair<String, String> =
        split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }

    private fun Uri.queryTreeDisplayName(): String? =
        DocumentsContract.buildDocumentUriUsingTree(
            this,
            DocumentsContract.getTreeDocumentId(this),
        ).queryDisplayName()

    // The grant behind a stored destination is not guaranteed to outlive the record that names it:
    // it is handed back once no live job needs the folder any more, and the user can revoke it - or
    // uninstall the provider - at any point. Every caller already degrades a missing name to the
    // document id or the app label, so a provider that refuses to answer takes that same path
    // instead of the whole resolve() throwing at whoever is rendering the destination.
    private fun Uri.queryDisplayName(): String? =
        try {
            context.contentResolver.query(
                this,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    }
}

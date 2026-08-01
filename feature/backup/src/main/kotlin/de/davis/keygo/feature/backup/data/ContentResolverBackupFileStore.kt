package de.davis.keygo.feature.backup.data

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.BackupFileStore
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/** Runs a blocking SAF call off the caller's thread and folds it into a [Result]. */
private suspend inline fun <T> io(crossinline block: () -> T): Result<T, Throwable> =
    withContext(Dispatchers.IO) {
        runCatching { block() }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it) },
        )
    }

@Single
internal class ContentResolverBackupFileStore(
    private val context: Context,
) : BackupFileStore {

    override suspend fun read(uri: BackupDestinationUri): Result<String, Throwable> = io {
        context.contentResolver.openInputStream(uri.value.toUri())
            ?.use { it.readBytes().decodeToString() }
            ?: error("Unable to open input stream for ${uri.value}")
    }

    override suspend fun writeNewDocument(
        folder: BackupDestinationUri,
        fileName: String,
        mimeType: String,
        text: String,
    ): Result<Unit, Throwable> = io {
        val tree = folder.value.toUri()
        val directory = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val document = DocumentsContract.createDocument(
            context.contentResolver,
            directory,
            mimeType,
            fileName,
        ) ?: error("Unable to create document $fileName in ${folder.value}")

        context.contentResolver.openOutputStream(document)
            ?.use { it.write(text.encodeToByteArray()) }
            ?: error("Unable to open output stream for $document")
    }

    override suspend fun listBackups(
        folder: BackupDestinationUri,
        baseName: String,
    ): Result<List<BackupEntry>, Throwable> = io {
        val tree = folder.value.toUri()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith(baseName)) continue
                    val documentUri =
                        DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
                    add(BackupEntry(BackupDestinationUri(documentUri.toString()), name))
                }
            }
        } ?: emptyList()
    }

    override suspend fun delete(uri: BackupDestinationUri): Result<Unit, Throwable> = io {
        val deleted = DocumentsContract.deleteDocument(
            context.contentResolver,
            uri.value.toUri(),
        )
        if (!deleted) error("Unable to delete ${uri.value}")
    }
}

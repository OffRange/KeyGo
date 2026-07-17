package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupEntry

interface BackupFileStore {

    /** Reads a single document. [uri] must point at a document, not a folder (import path). */
    suspend fun read(uri: BackupDestinationUri): Result<String, Throwable>

    /**
     * Creates a new document named [fileName] inside the [folder] tree and writes [text] to it.
     * [folder] is a tree URI; the document is only materialised here, so an aborted wizard never
     * leaves an empty file behind.
     */
    suspend fun writeNewDocument(
        folder: BackupDestinationUri,
        fileName: String,
        mimeType: String,
        text: String,
    ): Result<Unit, Throwable>

    /** Lists documents inside [folder] whose display name starts with [baseName]. */
    suspend fun listBackups(
        folder: BackupDestinationUri,
        baseName: String,
    ): Result<List<BackupEntry>, Throwable>

    /** Deletes the document at [uri]. */
    suspend fun delete(uri: BackupDestinationUri): Result<Unit, Throwable>
}

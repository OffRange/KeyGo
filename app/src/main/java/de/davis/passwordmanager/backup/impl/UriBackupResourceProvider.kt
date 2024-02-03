package de.davis.passwordmanager.backup.impl

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import de.davis.passwordmanager.backup.BackupResourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class UriBackupResourceProvider(
    private var uri: Uri,
    private var contentResolver: ContentResolver
) : BackupResourceProvider {

    override suspend fun provideInputStream(): InputStream = contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("ContentResolver returned null")

    override suspend fun provideOutputStream(): OutputStream = contentResolver.openOutputStream(uri)
        ?: throw IllegalStateException("ContentResolver returned null")

    override suspend fun getFileName(): String = contentResolver.getFileName(uri) ?: "Unknown"

    private suspend fun ContentResolver.getFileName(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            var fileName: String? = null
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                val cursor: Cursor? = query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        @SuppressLint("Range")
                        fileName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                }
            } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
                fileName = uri.lastPathSegment
            }
            
            fileName
        }
}
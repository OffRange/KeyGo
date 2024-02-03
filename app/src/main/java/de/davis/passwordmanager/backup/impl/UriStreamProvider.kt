package de.davis.passwordmanager.backup.impl

import android.content.ContentResolver
import android.net.Uri
import de.davis.passwordmanager.backup.StreamProvider
import java.io.InputStream
import java.io.OutputStream

class UriStreamProvider(private var uri: Uri, private var contentResolver: ContentResolver) :
    StreamProvider {

    override suspend fun provideInputStream(): InputStream = contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("ContentResolver returned null")

    override suspend fun provideOutputStream(): OutputStream = contentResolver.openOutputStream(uri)
        ?: throw IllegalStateException("ContentResolver returned null")
}
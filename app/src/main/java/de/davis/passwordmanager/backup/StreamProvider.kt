package de.davis.passwordmanager.backup

import java.io.InputStream
import java.io.OutputStream

interface StreamProvider {

    suspend fun provideInputStream(): InputStream
    suspend fun provideOutputStream(): OutputStream
}
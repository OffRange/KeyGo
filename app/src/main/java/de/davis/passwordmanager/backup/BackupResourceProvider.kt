package de.davis.passwordmanager.backup

import java.io.InputStream
import java.io.OutputStream

interface BackupResourceProvider {

    suspend fun provideInputStream(): InputStream
    suspend fun provideOutputStream(): OutputStream

    suspend fun getFileName(): String
}
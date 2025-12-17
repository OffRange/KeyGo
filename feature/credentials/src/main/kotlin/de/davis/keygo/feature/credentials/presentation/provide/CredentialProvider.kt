package de.davis.keygo.feature.credentials.presentation.provide

import androidx.credentials.provider.BeginGetCredentialOption
import androidx.credentials.provider.PublicKeyCredentialEntry

internal interface CredentialProvider<in T : BeginGetCredentialOption> {

    suspend fun provideFor(option: T): List<PublicKeyCredentialEntry>
}
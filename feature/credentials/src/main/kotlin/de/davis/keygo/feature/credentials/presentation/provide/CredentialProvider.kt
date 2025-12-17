package de.davis.keygo.feature.credentials.presentation.provide

import androidx.credentials.provider.BeginGetCredentialOption
import androidx.credentials.provider.CredentialEntry

internal interface CredentialProvider<in T : BeginGetCredentialOption> {

    suspend fun provideFor(option: T): List<CredentialEntry>
}
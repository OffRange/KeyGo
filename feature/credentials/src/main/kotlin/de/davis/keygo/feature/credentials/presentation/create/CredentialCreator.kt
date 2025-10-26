package de.davis.keygo.feature.credentials.presentation.create

import androidx.credentials.provider.CreateEntry

internal interface CredentialCreator {

    fun create(): CreateEntry
}
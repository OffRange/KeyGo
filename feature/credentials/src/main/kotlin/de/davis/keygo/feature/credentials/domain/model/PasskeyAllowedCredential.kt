package de.davis.keygo.feature.credentials.domain.model

import kotlinx.serialization.Serializable

@Serializable
internal data class PasskeyAllowedCredential(
    internal val type: String,
    internal val id: String
)
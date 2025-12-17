package de.davis.keygo.feature.credentials.domain.model

import kotlinx.serialization.Serializable

@Serializable
internal data class PublicKeyCredentialRequestOptions(
    internal val rpId: String,
    internal val allowCredentials: List<PasskeyAllowedCredential>
)
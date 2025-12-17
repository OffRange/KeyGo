package de.davis.keygo.feature.credentials.domain.usecase

import de.davis.keygo.core.item.domain.model.PasskeyMetadata
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.feature.credentials.domain.model.PublicKeyCredentialRequestOptions
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.io.encoding.Base64

@Single
internal class GetPasskeys(
    private val passkeyRepository: PasskeyRepository,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    suspend operator fun invoke(requestJson: String): List<PasskeyMetadata> {
        val reqOptions = json.decodeFromString<PublicKeyCredentialRequestOptions>(requestJson)

        val base64DecodedCredIds = reqOptions.allowCredentials
            .takeIf { it.isNotEmpty() }
            ?.map { base64.decode(it.id) }
            ?.toSet()

        return passkeyRepository.getPasskeysForRP(reqOptions.rpId).filter { metadata ->
            base64DecodedCredIds?.any { it.contentEquals(metadata.credentialId) } ?: true
        }
    }
}
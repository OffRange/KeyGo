package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.feature.autofill.domain.SignatureInfoProvider
import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single

@Single
class IsAppLinkedToWebsiteUseCase(
    private val digitalAssetLinkCheck: DigitalAssetLinkRepository,
    private val signatureInfoProvider: SignatureInfoProvider,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke(
        packageName: String,
        domain: String
    ): Boolean = coroutineScope {
        val signatures = signatureInfoProvider.getSignatureInfo(packageName)
        if (signatures.isEmpty()) return@coroutineScope false

        signatures.any { sign ->
            digitalAssetLinkCheck.isLinked(
                packageName = packageName,
                signature = sign,
                domain = domain
            )
        }
    }
}
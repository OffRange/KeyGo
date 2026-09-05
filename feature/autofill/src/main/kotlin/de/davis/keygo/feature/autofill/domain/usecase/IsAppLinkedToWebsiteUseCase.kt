package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.autofill.domain.SignatureInfoProvider
import de.davis.keygo.feature.autofill.domain.model.WebsiteLinkStatus
import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository
import org.koin.core.annotation.Single

@Single
class IsAppLinkedToWebsiteUseCase(
    private val digitalAssetLinkCheck: DigitalAssetLinkRepository,
    private val signatureInfoProvider: SignatureInfoProvider,
) {

    suspend operator fun invoke(
        packageName: String,
        domain: String,
    ): WebsiteLinkStatus {
        val signatures = signatureInfoProvider.getSignatureInfo(packageName)
        if (signatures.isEmpty()) return WebsiteLinkStatus.NotLinked

        return digitalAssetLinkCheck.isLinked(
            packageName = packageName,
            domain = domain,
            signatures = signatures,
        ).fold(
            onSuccess = { if (it) WebsiteLinkStatus.Linked else WebsiteLinkStatus.NotLinked },
            onFailure = { WebsiteLinkStatus.Unverified }
        )
    }
}

package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.SignatureInfoProvider
import de.davis.keygo.feature.autofill.domain.model.WebsiteLinkStatus
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
    ): WebsiteLinkStatus = coroutineScope {
        val signatures = signatureInfoProvider.getSignatureInfo(packageName)
        if (signatures.isEmpty()) return@coroutineScope WebsiteLinkStatus.NotLinked

        var anyLookupFailed = false
        signatures.forEach { sign ->
            val verdict = digitalAssetLinkCheck.isLinked(
                packageName = packageName,
                signature = sign,
                domain = domain
            )

            when (verdict) {
                is Result.Success ->
                    if (verdict.success) return@coroutineScope WebsiteLinkStatus.Linked

                is Result.Failure -> anyLookupFailed = true
            }
        }

        if (anyLookupFailed) WebsiteLinkStatus.Unverified else WebsiteLinkStatus.NotLinked
    }
}

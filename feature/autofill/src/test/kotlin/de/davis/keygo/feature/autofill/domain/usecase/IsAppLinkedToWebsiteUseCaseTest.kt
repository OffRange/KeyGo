package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.feature.autofill.FakeDigitalAssetLinkRepository
import de.davis.keygo.core.feature.autofill.FakeSignatureInfoProvider
import de.davis.keygo.feature.autofill.domain.model.DigitalAssetLinkFailure
import de.davis.keygo.feature.autofill.domain.model.WebsiteLinkStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IsAppLinkedToWebsiteUseCaseTest {

    private val digitalAssetLinkRepository = FakeDigitalAssetLinkRepository()
    private val signatureInfoProvider = FakeSignatureInfoProvider()
    private lateinit var useCase: IsAppLinkedToWebsiteUseCase

    @BeforeTest
    fun setup() {
        useCase = IsAppLinkedToWebsiteUseCase(
            digitalAssetLinkRepository,
            signatureInfoProvider,
        )
    }

    @Test
    fun `empty signatures returns unlinked without querying digital asset links`() = runTest {
        signatureInfoProvider.signatures = mapOf(PACKAGE_NAME to emptySet())

        val result = useCase(PACKAGE_NAME, DOMAIN)

        assertEquals(WebsiteLinkStatus.NotLinked, result)
        assertTrue(digitalAssetLinkRepository.lookups.isEmpty())
    }

    @Test
    fun `a linked signature returns linked`() = runTest {
        signatureInfoProvider.signatures = mapOf(PACKAGE_NAME to setOf(SIGNATURE))
        digitalAssetLinkRepository.links = setOf(
            FakeDigitalAssetLinkRepository.Link(PACKAGE_NAME, DOMAIN, SIGNATURE),
        )

        assertEquals(WebsiteLinkStatus.Linked, useCase(PACKAGE_NAME, DOMAIN))
    }

    @Test
    fun `an unlinked app returns unlinked`() = runTest {
        signatureInfoProvider.signatures = mapOf(PACKAGE_NAME to setOf(SIGNATURE, OTHER_SIGNATURE))
        digitalAssetLinkRepository.links = emptySet()

        assertEquals(WebsiteLinkStatus.NotLinked, useCase(PACKAGE_NAME, DOMAIN))
    }

    @Test
    fun `every signature is offered to the site in one lookup`() = runTest {
        val signatures = setOf(SIGNATURE, OTHER_SIGNATURE)
        signatureInfoProvider.signatures = mapOf(PACKAGE_NAME to signatures)
        digitalAssetLinkRepository.links = setOf(
            FakeDigitalAssetLinkRepository.Link(PACKAGE_NAME, DOMAIN, OTHER_SIGNATURE),
        )

        assertEquals(WebsiteLinkStatus.Linked, useCase(PACKAGE_NAME, DOMAIN))
        assertEquals(
            listOf(FakeDigitalAssetLinkRepository.Lookup(PACKAGE_NAME, DOMAIN, signatures)),
            digitalAssetLinkRepository.lookups,
        )
    }

    @Test
    fun `a failed lookup is unverified rather than unlinked`() = runTest {
        signatureInfoProvider.signatures = mapOf(PACKAGE_NAME to setOf(SIGNATURE))
        digitalAssetLinkRepository.failingDomains = setOf(DOMAIN)

        assertEquals(WebsiteLinkStatus.Unverified, useCase(PACKAGE_NAME, DOMAIN))
    }

    @Test
    fun `a failed lookup is never treated as linked`() = runTest {
        signatureInfoProvider.signatures = mapOf(PACKAGE_NAME to setOf(SIGNATURE))
        digitalAssetLinkRepository.links = setOf(
            FakeDigitalAssetLinkRepository.Link(PACKAGE_NAME, DOMAIN, SIGNATURE),
        )
        digitalAssetLinkRepository.failingDomains = setOf(DOMAIN)
        digitalAssetLinkRepository.failure = DigitalAssetLinkFailure.NoVerdict

        assertEquals(WebsiteLinkStatus.Unverified, useCase(PACKAGE_NAME, DOMAIN))
    }

    companion object {

        private const val PACKAGE_NAME = "com.example.app"

        private const val DOMAIN = "https://example.com"

        private const val SIGNATURE = "A1:B2:C3:D4"

        private const val OTHER_SIGNATURE = "E5:F6:07:18"
    }
}

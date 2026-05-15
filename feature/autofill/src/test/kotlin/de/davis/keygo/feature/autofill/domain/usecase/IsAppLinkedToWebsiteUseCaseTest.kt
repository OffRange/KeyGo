package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.feature.autofill.FakeDigitalAssetLinkRepository
import de.davis.keygo.core.feature.autofill.FakeSignatureInfoProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun `empty signatures returns false without querying digital asset links`() = runTest {
        val packageName = "com.example.app"
        val domain = "example.com"
        signatureInfoProvider.signatures = mapOf(packageName to emptySet())

        val result = useCase(packageName, domain)

        assertFalse(result)
        assertTrue(digitalAssetLinkRepository.linkedCalls.isEmpty())
    }

    @Test
    fun `one linked signature returns true`() = runTest {
        val packageName = "com.example.app"
        val signature = "ABCD1234"
        val domain = "example.com"
        signatureInfoProvider.signatures = mapOf(packageName to setOf(signature))
        digitalAssetLinkRepository.linkedTriples = setOf(Triple(packageName, signature, domain))

        val result = useCase(packageName, domain)

        assertTrue(result)
    }

    @Test
    fun `all unlinked signatures returns false`() = runTest {
        val packageName = "com.example.app"
        val sig1 = "SIGNATURE_1"
        val sig2 = "SIGNATURE_2"
        val domain = "example.com"
        signatureInfoProvider.signatures = mapOf(packageName to setOf(sig1, sig2))
        digitalAssetLinkRepository.linkedTriples = emptySet()

        val result = useCase(packageName, domain)

        assertFalse(result)
    }

    @Test
    fun `first linked signature returns true`() = runTest {
        val packageName = "com.example.app"
        val sig1 = "SIGNATURE_1"
        val sig2 = "SIGNATURE_2"
        val domain = "example.com"
        signatureInfoProvider.signatures = mapOf(packageName to setOf(sig1, sig2))
        digitalAssetLinkRepository.linkedTriples = setOf(Triple(packageName, sig1, domain))

        val result = useCase(packageName, domain)

        assertTrue(result)
    }
}

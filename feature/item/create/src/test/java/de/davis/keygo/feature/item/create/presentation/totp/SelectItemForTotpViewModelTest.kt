package de.davis.keygo.feature.item.create.presentation.totp

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.security.domain.usecase.GetTdlMatchedLoginsUseCase
import de.davis.keygo.feature.item.create.presentation.TestRegistrableDomainResolver
import de.davis.keygo.rust.FakeTotpService
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers what the picker knows before the user has chosen anything: which logins the scanned code
 * points at, and what happens when the code cannot be read at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectItemForTotpViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private val vaultId = newVaultId()
    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val totpService = FakeTotpService()
    private val domainResolver = TestRegistrableDomainResolver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `logins on the code's domain are suggested`() = runVmTest {
        val onDomain = seedLogin(name = "GitHub", domain = "github.com")
        seedLogin(name = "Google", domain = "google.com")
        totpService.infoFromUriResult = totpInfo(issuer = "github.com")

        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(setOf(onDomain), viewModel.state.value.suggestedItemIds)
    }

    @Test
    fun `a code without an issuer suggests on the account name's domain`() = runVmTest {
        val onDomain = seedLogin(name = "GitHub", domain = "github.com")
        totpService.infoFromUriResult = totpInfo(issuer = null, accountName = "me@github.com")

        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(setOf(onDomain), viewModel.state.value.suggestedItemIds)
    }

    @Test
    fun `a code that matches no domain suggests nothing`() = runVmTest {
        seedLogin(name = "Google", domain = "google.com")
        totpService.infoFromUriResult = totpInfo(issuer = null, accountName = "no-domain-here")

        val viewModel = buildViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(emptySet(), state.suggestedItemIds)
        assertFalse(state.parseError)
    }

    @Test
    fun `an unreadable code surfaces the parse error`() = runVmTest {
        totpService.infoFromUriResult = null

        val viewModel = buildViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.parseError)
        assertEquals(emptySet(), state.suggestedItemIds)
    }

    @Test
    fun `dismissing the parse error clears it`() = runVmTest {
        totpService.infoFromUriResult = null
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onParseErrorDismissed()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.parseError)
    }

    // Helpers

    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest(mainDispatcher.scheduler) { body() }

    private fun buildViewModel(uri: String = DEEP_LINK_URI) = SelectItemForTotpViewModel(
        totpUri = uri,
        totpService = totpService,
        getTdlMatchedLogins = GetTdlMatchedLoginsUseCase(domainResolver, loginRepository),
    )

    private fun seedLogin(name: String, domain: String): ItemId {
        val id = newItemId()
        loginRepository.seed(
            Login(
                id = id,
                name = name,
                username = null,
                domainInfos = setOf(
                    DomainInfo(
                        loginId = id,
                        value = domain,
                        eTLD1 = domainResolver.resolve(domain),
                    ),
                ),
                passwordCredential = null,
                totp = null,
                passkeys = emptySet(),
                note = null,
                pinned = false,
                vaultId = vaultId,
                keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
                timestamp = Timestamp(),
            ),
        )
        return id
    }

    private fun totpInfo(
        issuer: String?,
        accountName: String = "me@github.com",
        secret: String = "JBSWY3DPEHPK3PXP",
    ) = TotpInfo(
        secret = secret,
        issuer = issuer,
        accountName = accountName,
        algorithm = Algorithm.SHA1,
        digits = 6,
        period = 30,
    )

    companion object {
        private const val DEEP_LINK_URI =
            "otpauth://totp/GitHub:me@github.com?secret=JBSWY3DPEHPK3PXP&issuer=github.com"
    }
}

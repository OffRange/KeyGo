package de.davis.keygo.feature.item.create.presentation.login

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.usecase.ItemWithCryptoScopeUseCase
import de.davis.keygo.core.util.FakeRegistrableDomainResolver
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.feature.item.core.presentation.login.model.FieldType
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.create.presentation.login.model.DialogState
import de.davis.keygo.feature.item.create.presentation.login.model.LoginBaseState
import de.davis.keygo.feature.item.create.presentation.login.model.LoginUiEvent
import de.davis.keygo.feature.item.create.presentation.model.ItemUiEvent
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState
import de.davis.keygo.rust.FakeTotpService
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers what the login form does with a code the picker handed it: a new item is prefilled from
 * the code, and a chosen item has it folded in, raising the override dialog when the two collide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private val defaultVault = Vault(
        id = newVaultId(),
        name = "Default vault",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val cryptoProvider = FakeCryptographicScopeProvider(itemRepository)
    private val totpService = FakeTotpService()
    private val domainResolver = FakeRegistrableDomainResolver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        vaultRepository.seed(defaultVault)
        vaultContextRepository.seedLastInteracted(defaultVault.id)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `back leaves the screen when the code was scanned into an open form`() = runVmTest {
        totpService.infoFromUriResult = totpInfo(issuer = "github.com")
        val viewModel = buildViewModel()
        backgroundScope.launch(mainDispatcher) { viewModel.state.collect { } }
        viewModel.init(DetailPaneInformation.Init.New(VaultItemType.Login))
        advanceUntilIdle()
        val navigation = collectNavigation(viewModel)
        viewModel.onEvent(LoginUiEvent.OnCodesScanned(listOf(DEEP_LINK_URI)))
        advanceUntilIdle()

        viewModel.onEvent(LoginUiEvent.ItemUi(ItemUiEvent.OnBackClick))
        advanceUntilIdle()

        assertEquals(listOf<ItemId?>(null), navigation)
    }

    @Test
    fun `a new item is prefilled from the picker's code`() = runVmTest {
        totpService.infoFromUriResult = totpInfo(issuer = "github.com")

        val viewModel = buildViewModel()
        backgroundScope.launch(mainDispatcher) { viewModel.state.collect { } }
        viewModel.init(
            DetailPaneInformation.Init.New(
                itemType = VaultItemType.Login,
                pendingTotpUri = DEEP_LINK_URI,
            ),
        )
        advanceUntilIdle()

        val base = viewModel.readyBase()
        assertEquals(DEEP_LINK_URI, base.totpTextFieldState.text.toString())
        assertEquals("me@github.com", base.usernameTextFieldState.text.toString())
        assertEquals(setOf("github.com"), base.domains.mapTo(mutableSetOf()) { it.value })
    }

    @Test
    fun `an existing item is loaded and the picker's code folded in`() = runVmTest {
        val existing = seedLogin(name = "GitHub", domain = "github.com")
        totpService.infoFromUriResult = totpInfo(issuer = "github.com")

        val viewModel = buildViewModel()
        backgroundScope.launch(mainDispatcher) { viewModel.state.collect { } }
        viewModel.init(
            DetailPaneInformation.Init.Existing(
                itemType = VaultItemType.Login,
                id = existing,
                pendingTotpUri = DEEP_LINK_URI,
            ),
        )
        advanceUntilIdle()

        val state = viewModel.readyState()
        assertTrue(state.base.updating)
        assertEquals("GitHub", state.shared.nameTextFieldState.text.toString())
        assertEquals(DEEP_LINK_URI, state.base.totpTextFieldState.text.toString())
        assertEquals("me@github.com", state.base.usernameTextFieldState.text.toString())
    }

    @Test
    fun `a code that collides with the chosen item raises the override dialog`() = runVmTest {
        val existing = seedLogin(
            name = "GitHub",
            domain = "github.com",
            username = "old@github.com",
        )
        totpService.infoFromUriResult = totpInfo(issuer = "github.com")

        val viewModel = buildViewModel()
        backgroundScope.launch(mainDispatcher) { viewModel.state.collect { } }
        viewModel.init(
            DetailPaneInformation.Init.Existing(
                itemType = VaultItemType.Login,
                id = existing,
                pendingTotpUri = DEEP_LINK_URI,
            ),
        )
        advanceUntilIdle()

        val dialog = viewModel.readyBase().dialogState
        assertIs<DialogState.OverrideTotp>(dialog)
        val usernameField = dialog.fields.single { it.fieldType == FieldType.Username }
        assertEquals("old@github.com", usernameField.before)
        assertEquals("me@github.com", usernameField.after)
    }

    // Helpers

    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest(mainDispatcher.scheduler) { body() }

    /**
     * Records what the screen asks navigation to do. Leaving raises `null`, a saved item raises its
     * id, and an empty list is the assertion that the screen stayed where it was.
     */
    private fun TestScope.collectNavigation(viewModel: LoginViewModel): List<ItemId?> {
        val events = mutableListOf<ItemId?>()
        backgroundScope.launch(mainDispatcher) { viewModel.itemCreatedEvent.collect { events += it } }
        return events
    }

    private fun LoginViewModel.readyState(): ItemUiState.Ready<LoginBaseState> {
        val state = state.value
        assertIs<ItemUiState.Ready<LoginBaseState>>(state)
        return state
    }

    private fun LoginViewModel.readyBase(): LoginBaseState = readyState().base

    private fun seedLogin(
        name: String,
        domain: String,
        username: String? = null,
    ): ItemId {
        val id = newItemId()
        loginRepository.seed(
            Login(
                id = id,
                name = name,
                username = username,
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
                vaultId = defaultVault.id,
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

    private fun buildViewModel() = LoginViewModel(
        itemWithCryptoScope = ItemWithCryptoScopeUseCase(vaultRepository, cryptoProvider),
        loginRepository = loginRepository,
        passwordStrengthEstimator = FakePasswordStrengthEstimator(),
        createNewOrUpdateLogin = CreateNewOrUpdateLoginUseCase(
            cryptographicScopeProvider = cryptoProvider,
            loginRepository = loginRepository,
            vaultRepository = vaultRepository,
            upsertVaultItem = UpsertVaultItemUseCase(
                loginRepository,
                FakeCreditCardRepository(),
            ),
            passwordStrengthEstimator = FakePasswordStrengthEstimator(),
            totpService = totpService,
        ),
        snackbarManager = TestSnackbarManager(),
        totpService = totpService,
        registrableDomainResolver = domainResolver,
        vaultContextRepository = vaultContextRepository,
        itemRepository = itemRepository,
        observeAllTags = ObserveAllTagsSortedUseCase(itemRepository, SortUseCase()),
        vaultRepository = vaultRepository,
    )

    private class TestSnackbarManager : SnackbarManager {
        override val oneShotEvents: Flow<SnackbarMessage> = emptyFlow()
        override fun sendMessage(message: SnackbarMessage) = Unit
    }

    companion object {
        private const val DEEP_LINK_URI =
            "otpauth://totp/GitHub:me@github.com?secret=JBSWY3DPEHPK3PXP&issuer=github.com"
    }
}

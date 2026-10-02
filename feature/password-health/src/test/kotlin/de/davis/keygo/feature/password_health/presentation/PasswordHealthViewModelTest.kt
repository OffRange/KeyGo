package de.davis.keygo.feature.password_health.presentation

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.model.ItemKeyEnvelope
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.FakeRegistrableDomainResolver
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.feature.item.view.domain.WebsiteHandler
import de.davis.keygo.feature.password_health.FakeBreachedRepository
import de.davis.keygo.feature.password_health.FakeConnectivityRepository
import de.davis.keygo.feature.password_health.FakeHealthCheckNotifierScheduler
import de.davis.keygo.feature.password_health.FakeHealthNotificationStateRepository
import de.davis.keygo.feature.password_health.FakeHealthSettingsRepository
import de.davis.keygo.feature.password_health.FakeNotifier
import de.davis.keygo.feature.password_health.data.LoginFingerprinterImpl
import de.davis.keygo.feature.password_health.domain.PasswordHealthAttention
import de.davis.keygo.feature.password_health.domain.checker.BreachedPasswordCheck
import de.davis.keygo.feature.password_health.domain.checker.ReusePasswordCheck
import de.davis.keygo.feature.password_health.domain.checker.WeakPasswordChecker
import de.davis.keygo.feature.password_health.domain.model.FindingSeverity
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthReportStoreError
import de.davis.keygo.feature.password_health.domain.model.PasswordFixError
import de.davis.keygo.feature.password_health.domain.model.PasswordHealthReportError
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import de.davis.keygo.feature.password_health.domain.report.HealthReportAssembler
import de.davis.keygo.feature.password_health.domain.report.HealthReportScanner
import de.davis.keygo.feature.password_health.domain.report.VAULT_ID
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.login
import de.davis.keygo.feature.password_health.domain.repository.HealthReportStoreRepository
import de.davis.keygo.feature.password_health.domain.usecase.PasswordHealthReportUseCase
import de.davis.keygo.feature.password_health.domain.usecase.SetHealthNotificationsUseCase
import de.davis.keygo.feature.password_health.presentation.model.FixFlow
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthStatus
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.model.RunPhase
import de.davis.keygo.rust.FakeTotpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordHealthViewModelTest {

    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val scopeProvider = FakeCryptographicScopeProvider(itemRepository)
    private val vaultRepository = FakeVaultRepository()
    private val breachState = FakeHealthSettingsRepository(breachesEnabled = false)
    private val breached = FakeBreachedRepository()
    private val websiteHandler = RecordingWebsiteHandler()
    private val notificationState = FakeHealthNotificationStateRepository()
    private val scheduler = FakeHealthCheckNotifierScheduler()
    private val connectivity = FakeConnectivityRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vaultRepository.seed(
            Vault(
                id = VAULT_ID,
                name = "Vault",
                keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
                icon = Vault.Icon.Default,
            ),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PasswordHealthViewModel(
        passwordHealth = PasswordHealthReportUseCase(
            loginRepository = loginRepository,
            loginFingerprinter = LoginFingerprinterImpl(),
            healthReportStoreRepository = InMemoryStore(),
            healthSettingsRepository = breachState,
            scanner = HealthReportScanner(
                scopeProvider,
                listOf(
                    WeakPasswordChecker(),
                    ReusePasswordCheck(),
                    BreachedPasswordCheck(breached, breachState, connectivity),
                ),
            ),
            assembler = HealthReportAssembler(),
            attention = PasswordHealthAttention(
                FakeSession(),
                CoroutineScope(Dispatchers.Unconfined)
            ),
            healthNotificationStateRepository = notificationState,
            healthCheckNotifierScheduler = scheduler,
        ),
        createNewOrUpdateLogin = CreateNewOrUpdateLoginUseCase(
            cryptographicScopeProvider = scopeProvider,
            loginRepository = loginRepository,
            vaultRepository = vaultRepository,
            upsertVaultItem = UpsertVaultItemUseCase(loginRepository, FakeCreditCardRepository()),
            passwordStrengthEstimator = FakePasswordStrengthEstimator(PasswordScore.Strong),
            totpService = FakeTotpService(),
            registrableDomainResolver = FakeRegistrableDomainResolver(),
        ),
        healthSettingsRepository = breachState,
        setHealthNotifications = SetHealthNotificationsUseCase(
            healthSettingsRepository = breachState,
            healthNotificationStateRepository = notificationState,
            scheduler = scheduler,
            notifier = FakeNotifier(),
        ),
        websiteHandler = websiteHandler,
        connectivityRepository = connectivity,
    )

    @Test
    fun startsOnTheFirstLoad() = runTest {
        val vm = viewModel().also { subscribe(it) }

        assertEquals(RunPhase.FirstLoad, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.isFirstLoad)
    }

    @Test
    fun aRunReportsTheWeakPasswords() = runTest {
        seed(0, score = PasswordScore.Weak)
        seed(1)
        val vm = viewModel().also { subscribe(it) }

        val state = runAndAwait(vm)

        assertEquals(RunPhase.Idle, state.phase)
        assertEquals(2, state.totalPasswordCount)
        assertEquals(PasswordHealthStatus.NEEDS_ATTENTION, state.status)
        assertEquals(listOf(FindingSeverity.Medium), state.healthSections.map { it.severity })
        assertEquals(listOf(id(0)), state.healthSections.single().standalone.map { it.itemId })
        assertNull(state.error)
    }

    @Test
    fun reusedPasswordsAreReportedAsAGroup() = runTest {
        seed(0, password = "same")
        seed(1, password = "same")
        val vm = viewModel().also { subscribe(it) }

        val state = runAndAwait(vm)

        assertEquals(2, state.summary.reused)
        assertEquals(FindingSeverity.High, state.healthSections.single().severity)
    }

    @Test
    fun aCleanVaultIsAllGood() = runTest {
        seed(0)
        val vm = viewModel().also { subscribe(it) }

        assertEquals(PasswordHealthStatus.ALL_GOOD, runAndAwait(vm).status)
    }

    @Test
    fun anEmptyVaultHasNoData() = runTest {
        val vm = viewModel().also { subscribe(it) }

        val state = runAndAwait(vm)

        assertEquals(PasswordHealthReportError.NoPasswords, state.error)
        assertEquals(PasswordHealthStatus.NO_DATA, state.status)
        assertEquals(0, state.totalPasswordCount)
    }

    @Test
    fun aRefreshEndsIdleWithTheNewReport() = runTest {
        seed(0)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)

        seed(1, score = PasswordScore.Weak)
        vm.onEvent(PasswordHealthUiEvent.RefreshHealthCheck)
        val state = vm.awaitIdle { it.totalPasswordCount == 2 }

        assertEquals(PasswordHealthStatus.NEEDS_ATTENTION, state.status)
    }

    @Test
    fun aRefreshLooksUpEveryPasswordInBreachesAgain() = runTest {
        seed(0)
        seed(1)
        breachState.setBreachEnabled(true)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)
        assertEquals(2, breachLookups())

        vm.onEvent(PasswordHealthUiEvent.RefreshHealthCheck)
        vm.awaitIdle { breachLookups() == 4 }
    }

    @Test
    fun theRefreshAfterAFixOnlyLooksUpTheChangedPassword() = runTest {
        seed(0, score = PasswordScore.Weak)
        seed(1)
        breachState.setBreachEnabled(true)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)

        vm.fix(0, "n3w-Pa55word!")
        vm.awaitIdle { it.fixFlow == null && it.optimisticallyFixed.isEmpty() }

        assertEquals(3, breachLookups())
    }

    @Test
    fun theBreachCheckStateIsMirrored() = runTest {
        val vm = viewModel().also { subscribe(it) }

        assertEquals(false, vm.uiState.value.breachCheckEnabled)
        breachState.setBreachEnabled(true)
        assertEquals(true, vm.uiState.first { it.breachCheckEnabled }.breachCheckEnabled)
    }

    @Test
    fun turningTheBreachCheckOnRunsAHealthCheck() = runTest {
        seed(0, score = PasswordScore.Weak)
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.OnBreachCheckChanged(true))
        val state = vm.awaitIdle { it.totalPasswordCount == 1 }

        assertEquals(true, breachState.state.value.breachesEnabled)
        assertEquals(true, state.breachCheckEnabled)
    }

    @Test
    fun turningTheBreachCheckOffDoesNotRunAHealthCheck() = runTest {
        breachState.setBreachEnabled(true)
        seed(0)
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.OnBreachCheckChanged(false))

        assertEquals(false, breachState.state.value.breachesEnabled)
        assertEquals(RunPhase.FirstLoad, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.totalPasswordCount)
    }

    @Test
    fun reconnectingRetriesOnlyTheUnreachableBreachLookups() = runTest {
        seed(0)
        seed(1)
        breachState.setBreachEnabled(true)
        connectivity.online = false
        val vm = viewModel().also { subscribe(it) }
        assertEquals(GapReason.Unreachable, runAndAwait(vm).breachGap?.reason)

        connectivity.online = true
        val state = vm.awaitIdle { it.breachGap == null }

        assertEquals(2, breachLookups())
        assertEquals(2, state.totalPasswordCount)
    }

    @Test
    fun aConnectionThatDropsBeforeSettlingIsNotRetried() = runTest {
        seed(0)
        breachState.setBreachEnabled(true)
        connectivity.online = false
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)

        connectivity.online = true
        advanceTimeBy(500.milliseconds)
        connectivity.online = false
        advanceUntilIdle()

        assertEquals(0, breachLookups())
        assertEquals(GapReason.Unreachable, vm.uiState.value.breachGap?.reason)
    }

    @Test
    fun reconnectingWithoutAnUnreachableGapDoesNotRun() = runTest {
        seed(0)
        connectivity.online = false
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)

        connectivity.online = true
        advanceUntilIdle()

        assertEquals(RunPhase.Idle, vm.uiState.value.phase)
        assertEquals(0, breachLookups())
    }

    @Test
    fun comingOnlineRightAfterOpeningStillRetries() = runTest {
        seed(0)
        breachState.setBreachEnabled(true)
        connectivity.online = false
        val vm = viewModel().also { subscribe(it) }
        assertEquals(GapReason.Unreachable, runAndAwait(vm).breachGap?.reason)

        connectivity.online = true
        vm.awaitIdle { it.breachGap == null }

        assertEquals(1, breachLookups())
    }

    @Test
    fun clickingAnItemOpensIt() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.ItemClicked(id(3)))

        assertEquals(PasswordHealthEvent.OpenItem(id(3)), vm.events.first())
    }

    @Test
    fun openingASiteGoesToTheWebsiteHandler() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.OpenSite("example.com"))

        assertEquals(listOf("example.com"), websiteHandler.opened)
    }

    @Test
    fun fixingStartsByGeneratingAPassword() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.FixClicked(id(0)))

        assertEquals(FixFlow.Generating(id(0)), vm.uiState.value.fixFlow)
    }

    @Test
    fun aGeneratedPasswordWaitsForConfirmation() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.FixClicked(id(0)))
        vm.onEvent(PasswordHealthUiEvent.PasswordGenerated("n3w-Pa55word!"))

        assertEquals(FixFlow.Pending(id(0), "n3w-Pa55word!"), vm.uiState.value.fixFlow)
    }

    @Test
    fun aGeneratedPasswordWithoutAFixIsIgnored() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.PasswordGenerated("n3w-Pa55word!"))

        assertNull(vm.uiState.value.fixFlow)
    }

    @Test
    fun dismissingTheGeneratorCancelsTheFix() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.FixClicked(id(0)))
        vm.onEvent(PasswordHealthUiEvent.DismissGeneratePassword)

        assertNull(vm.uiState.value.fixFlow)
    }

    @Test
    fun dismissingTheGeneratorKeepsAPendingFix() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.FixClicked(id(0)))
        vm.onEvent(PasswordHealthUiEvent.PasswordGenerated("new"))
        vm.onEvent(PasswordHealthUiEvent.DismissGeneratePassword)

        assertEquals(FixFlow.Pending(id(0), "new"), vm.uiState.value.fixFlow)
    }

    @Test
    fun discardingAPendingFixDropsIt() = runTest {
        val vm = viewModel().also { subscribe(it) }

        vm.onEvent(PasswordHealthUiEvent.FixClicked(id(0)))
        vm.onEvent(PasswordHealthUiEvent.PasswordGenerated("new"))
        vm.onEvent(PasswordHealthUiEvent.DiscardFix)

        assertNull(vm.uiState.value.fixFlow)
    }

    @Test
    fun confirmingWithoutAPendingFixDoesNothing() = runTest {
        seed(0, score = PasswordScore.Weak)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)

        vm.onEvent(PasswordHealthUiEvent.FixClicked(id(0)))
        vm.onEvent(PasswordHealthUiEvent.ConfirmPasswordChanged)

        assertEquals(FixFlow.Generating(id(0)), vm.uiState.value.fixFlow)
        assertEquals("password-${id(0)}", passwordOf(0))
    }

    @Test
    fun aConfirmedFixSavesThePasswordAndClearsTheFinding() = runTest {
        seed(0, score = PasswordScore.Weak)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)

        vm.fix(0, "n3w-Pa55word!")
        val state = vm.awaitIdle {
            it.fixFlow == null && it.optimisticallyFixed.isEmpty() && it.healthSections.isEmpty()
        }

        assertEquals("n3w-Pa55word!", passwordOf(0))
        assertEquals(PasswordHealthStatus.ALL_GOOD, state.status)
        assertTrue(state.optimisticallyFixed.isEmpty())
    }

    @Test
    fun aLockedVaultKeepsTheFixPendingAsLocked() = runTest {
        seed(0, score = PasswordScore.Weak)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)
        scopeProvider.itemScopeFailure = CryptoScopeError.IdNotFound

        vm.fix(0, "new")
        val state = vm.uiState.first { (it.fixFlow as? FixFlow.Pending)?.error != null }

        assertEquals(
            FixFlow.Pending(id(0), "new", applying = false, error = PasswordFixError.Locked),
            state.fixFlow,
        )
        assertTrue(state.optimisticallyFixed.isEmpty())
        assertEquals(listOf(id(0)), state.healthSections.single().standalone.map { it.itemId })
    }

    @Test
    fun aFailedSaveKeepsTheFixPendingAsASaveError() = runTest {
        seed(0, score = PasswordScore.Weak)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)
        loginRepository.createOrUpdateError = IllegalStateException("disk full")

        vm.fix(0, "new")
        val state = vm.uiState.first { (it.fixFlow as? FixFlow.Pending)?.error != null }

        assertEquals(PasswordFixError.Save, (state.fixFlow as FixFlow.Pending).error)
        assertEquals("password-${id(0)}", passwordOf(0))
    }

    @Test
    fun fixingAnItemThatIsGoneIsASaveError() = runTest {
        seed(0, score = PasswordScore.Weak)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)

        vm.fix(9, "new")
        val state = vm.uiState.first { (it.fixFlow as? FixFlow.Pending)?.error != null }

        assertEquals(PasswordFixError.Save, (state.fixFlow as FixFlow.Pending).error)
    }

    @Test
    fun aFailedFixCanBeRetried() = runTest {
        seed(0, score = PasswordScore.Weak)
        val vm = viewModel().also { subscribe(it) }
        runAndAwait(vm)
        loginRepository.createOrUpdateError = IllegalStateException("disk full")
        vm.fix(0, "n3w-Pa55word!")
        vm.uiState.first { (it.fixFlow as? FixFlow.Pending)?.error != null }

        vm.onEvent(PasswordHealthUiEvent.ConfirmPasswordChanged)
        vm.awaitIdle {
            it.fixFlow == null && it.optimisticallyFixed.isEmpty() && it.healthSections.isEmpty()
        }

        assertEquals("n3w-Pa55word!", passwordOf(0))
    }

    private fun PasswordHealthViewModel.fix(n: Int, password: String) {
        onEvent(PasswordHealthUiEvent.FixClicked(id(n)))
        onEvent(PasswordHealthUiEvent.PasswordGenerated(password))
        onEvent(PasswordHealthUiEvent.ConfirmPasswordChanged)
    }

    private fun TestScope.subscribe(vm: PasswordHealthViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private suspend fun runAndAwait(vm: PasswordHealthViewModel): PasswordHealthUiState {
        vm.onEvent(PasswordHealthUiEvent.RunHealthCheck)
        return vm.uiState.first { it.phase == RunPhase.Idle }
    }

    private suspend fun PasswordHealthViewModel.awaitIdle(
        predicate: (PasswordHealthUiState) -> Boolean,
    ) = uiState.first { it.phase == RunPhase.Idle && predicate(it) }

    private fun breachLookups() = breached.calls.sumOf { it.suffixes.size }

    private fun seed(
        n: Int,
        password: String = "password-${id(n)}",
        score: PasswordScore = PasswordScore.Strong,
    ) {
        itemRepository.seedEnvelope(
            ItemKeyEnvelope(
                vaultId = VAULT_ID,
                itemId = id(n),
                itemKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
                vaultKeyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
            ),
        )
        loginRepository.seed(login(id(n), password = password, score = score))
    }

    private suspend fun passwordOf(n: Int): String {
        val login = loginRepository.getLoginById(id(n))!!
        return FakeCryptographicScopeProvider.transform(
            login.passwordCredential!!.secret.payload.ciphertext,
        ).decodeToString()
    }

    private class RecordingWebsiteHandler : WebsiteHandler {
        val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun openWebsite(url: String) {
            opened += url
        }
    }

    private class InMemoryStore : HealthReportStoreRepository {
        private var stored: StoredHealthReport? = null

        override suspend fun storeReport(report: StoredHealthReport): Result<Unit, HealthReportStoreError> {
            stored = report
            return Result.Success(Unit)
        }

        override suspend fun load(): Result<StoredHealthReport, HealthReportStoreError> =
            stored?.let { Result.Success(it) }
                ?: Result.Failure(HealthReportStoreError.NoReportStored)
    }
}

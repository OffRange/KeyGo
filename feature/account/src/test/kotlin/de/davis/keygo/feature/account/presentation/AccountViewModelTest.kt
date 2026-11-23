package de.davis.keygo.feature.account.presentation

import de.davis.keygo.core.sync.FakeServerLinkRepository
import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.account.presentation.models.AccountUiEvent
import de.davis.keygo.feature.account.presentation.models.DeviceSummary
import de.davis.keygo.feature.account.presentation.models.DevicesState
import de.davis.keygo.feature.account.presentation.models.LogoutDialogState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeServerLinkRepository()
    private val syncRepository = FakeSyncRepository(repository)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AccountViewModel(syncRepository, repository)

    @Test
    fun `without a link the state is none`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        assertEquals(ServerLink.None, vm.uiState.value.link)
    }

    @Test
    fun `a new link is shown as connected`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        val link = ServerLink.Connected(UUID.randomUUID(), "https://vault.example.org/", "ada")
        repository.link.value = link

        assertEquals(link, vm.uiState.value.link)
    }

    @Test
    fun `a removed link switches back to none`() = runTest(dispatcher) {
        repository.link.value =
            ServerLink.Connected(UUID.randomUUID(), "https://vault.example.org/", "ada")
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)
        assertIs<ServerLink.Connected>(vm.uiState.value.link)

        repository.link.value = ServerLink.None

        assertEquals(ServerLink.None, vm.uiState.value.link)
    }

    @Test
    fun `logout asks for confirmation first`() = runTest(dispatcher) {
        repository.link.value = connected
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        vm.onEvent(AccountUiEvent.OnLogoutClicked)

        assertEquals(LogoutDialogState.Confirming, vm.uiState.value.logoutDialog)
        assertEquals(0, syncRepository.logouts)
    }

    @Test
    fun `dismissing the confirmation keeps the link`() = runTest(dispatcher) {
        repository.link.value = connected
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        vm.onEvent(AccountUiEvent.OnLogoutClicked)
        vm.onEvent(AccountUiEvent.OnLogoutDismissed)

        assertEquals(LogoutDialogState.Hidden, vm.uiState.value.logoutDialog)
        assertEquals(connected, repository.link.value)
    }

    @Test
    fun `confirming logs out and closes the dialog`() = runTest(dispatcher) {
        repository.link.value = connected
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        vm.onEvent(AccountUiEvent.OnLogoutClicked)
        vm.onEvent(AccountUiEvent.OnLogoutConfirmed)

        assertEquals(LogoutDialogState.Hidden, vm.uiState.value.logoutDialog)
        assertEquals(ServerLink.None, repository.link.value)
    }

    @Test
    fun `an unreachable server still counts as logged out`() = runTest(dispatcher) {
        repository.link.value = connected
        syncRepository.result = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        vm.onEvent(AccountUiEvent.OnLogoutClicked)
        vm.onEvent(AccountUiEvent.OnLogoutConfirmed)

        assertEquals(LogoutDialogState.Hidden, vm.uiState.value.logoutDialog)
    }

    @Test
    fun `a session that could not be cleared shows the failure`() = runTest(dispatcher) {
        repository.link.value = connected
        syncRepository.clearsLink = false
        syncRepository.result = Result.Failure(SyncError.Internal("store refused"))
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        vm.onEvent(AccountUiEvent.OnLogoutClicked)
        vm.onEvent(AccountUiEvent.OnLogoutConfirmed)

        assertEquals(LogoutDialogState.Failed, vm.uiState.value.logoutDialog)
    }

    @Test
    fun `logging out cannot be dismissed or started twice`() = runTest(dispatcher) {
        repository.link.value = connected
        val gate = CompletableDeferred<Unit>()
        syncRepository.gate = gate
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        vm.onEvent(AccountUiEvent.OnLogoutClicked)
        vm.onEvent(AccountUiEvent.OnLogoutConfirmed)
        vm.onEvent(AccountUiEvent.OnLogoutConfirmed)
        vm.onEvent(AccountUiEvent.OnLogoutDismissed)

        assertEquals(LogoutDialogState.LoggingOut, vm.uiState.value.logoutDialog)
        gate.complete(Unit)
        assertEquals(1, syncRepository.logouts)
        assertEquals(LogoutDialogState.Hidden, vm.uiState.value.logoutDialog)
    }

    @Test
    fun `a connected account shows its device and sign-in count`() = runTest(dispatcher) {
        repository.link.value = connected
        syncRepository.devices = Result.Success(
            listOf(device(), device(), device(loggedIn = false)),
        )
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        assertEquals(
            DevicesState.Loaded(DeviceSummary(total = 3, signedIn = 2)),
            vm.uiState.value.devices,
        )
    }

    @Test
    fun `a failed device fetch is reported as failed`() = runTest(dispatcher) {
        repository.link.value = connected
        syncRepository.devices = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        assertEquals(DevicesState.Failed, vm.uiState.value.devices)
    }

    @Test
    fun `the link shows before the device count arrives`() = runTest(dispatcher) {
        repository.link.value = connected
        syncRepository.devices = Result.Success(listOf(device(), device()))
        val gate = CompletableDeferred<Unit>()
        syncRepository.devicesGate = gate
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        assertEquals(connected, vm.uiState.value.link)
        assertEquals(DevicesState.Loading, vm.uiState.value.devices)

        gate.complete(Unit)
        assertEquals(
            DevicesState.Loaded(DeviceSummary(total = 2, signedIn = 2)),
            vm.uiState.value.devices,
        )
    }

    private class FakeSyncRepository(private val links: FakeServerLinkRepository) : SyncRepository {
        var result: Result<Unit, SyncError> = Result.Success(Unit)
        var clearsLink = true
        var gate: CompletableDeferred<Unit>? = null
        var logouts = 0

        override suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError> =
            error("not used")

        override suspend fun register(
            serverUrl: String,
            userId: UUID,
            username: String,
            password: String,
            invite: String?,
        ): Result<SyncAccount, SyncError> = error("not used")

        var devices: Result<List<Device>, SyncError> = Result.Success(emptyList())
        var devicesGate: CompletableDeferred<Unit>? = null

        override suspend fun devices(): Result<List<Device>, SyncError> {
            devicesGate?.await()
            return devices
        }

        override suspend fun logout(): Result<Unit, SyncError> {
            logouts++
            gate?.await()
            if (clearsLink) links.link.value = ServerLink.None
            return result
        }
    }

    private companion object {
        val connected =
            ServerLink.Connected(UUID.randomUUID(), "https://vault.example.org/", "ada")

        fun device(loggedIn: Boolean = true) = Device(
            id = UUID.randomUUID(),
            name = "Pixel",
            platform = "android",
            appVersion = "2.2.0",
            createdAt = Instant.fromEpochSeconds(1_700_000_000),
            lastSeenAt = Instant.fromEpochSeconds(1_800_000_000),
            authTime = null,
            loggedIn = loggedIn,
        )
    }
}

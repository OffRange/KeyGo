package de.davis.keygo.feature.account.presentation.devices

import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.account.presentation.devices.model.DeviceActivity
import de.davis.keygo.feature.account.presentation.devices.model.DeviceItem
import de.davis.keygo.feature.account.presentation.devices.model.DevicesEvent
import de.davis.keygo.feature.account.presentation.devices.model.DevicesState
import de.davis.keygo.feature.account.presentation.devices.model.DevicesUiEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
class DevicesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val syncRepository = FakeSyncRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // Mirrors DevicesScreen, whose first resume starts the first load.
    private fun viewModel() = DevicesViewModel(syncRepository).also {
        it.onEvent(DevicesUiEvent.OnResumed)
    }

    @Test
    fun `nothing is loaded before the screen resumes`() = runTest(dispatcher) {
        val vm = DevicesViewModel(syncRepository)

        assertEquals(DevicesState.Loading, vm.uiState.value.devices)
        assertEquals(0, syncRepository.loads)
    }

    @Test
    fun `devices are loading until the server answers`() = runTest(dispatcher) {
        syncRepository.gate = CompletableDeferred()

        val vm = viewModel()

        assertEquals(DevicesState.Loading, vm.uiState.value.devices)
        assertEquals(false, vm.uiState.value.isRefreshing)
    }

    @Test
    fun `pulling to refresh keeps the list and shows the refresh until the server answers`() =
        runTest(dispatcher) {
            val first = device(current = true)
            syncRepository.devices = Result.Success(listOf(first))
            val vm = viewModel()

            val second = device(current = true)
            syncRepository.devices = Result.Success(listOf(second))
            syncRepository.gate = CompletableDeferred()
            vm.onEvent(DevicesUiEvent.OnRefresh)

            assertEquals(true, vm.uiState.value.isRefreshing)
            assertEquals(onlyThisDevice(first), vm.uiState.value.devices)

            syncRepository.gate?.complete(Unit)

            assertEquals(false, vm.uiState.value.isRefreshing)
            assertEquals(onlyThisDevice(second), vm.uiState.value.devices)
        }

    @Test
    fun `a failed pull keeps the list on screen`() = runTest(dispatcher) {
        val current = device(current = true)
        syncRepository.devices = Result.Success(listOf(current))
        val vm = viewModel()

        syncRepository.devices = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        vm.onEvent(DevicesUiEvent.OnRefresh)

        assertEquals(false, vm.uiState.value.isRefreshing)
        assertEquals(onlyThisDevice(current), vm.uiState.value.devices)
        assertEquals(DevicesEvent.RefreshFailed, vm.event.first())
    }

    @Test
    fun `a failed re-read on resume stays silent`() = runTest(dispatcher) {
        syncRepository.devices = Result.Success(listOf(device(current = true)))
        val vm = viewModel()
        val events = mutableListOf<DevicesEvent>()
        val collector = launch { vm.event.toList(events) }

        syncRepository.devices = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        vm.onEvent(DevicesUiEvent.OnResumed)

        assertEquals(emptyList(), events)
        collector.cancel()
    }

    @Test
    fun `resuming during a silent re-read does not show the refresh indicator`() =
        runTest(dispatcher) {
            syncRepository.devices = Result.Success(listOf(device(current = true)))
            val vm = viewModel()

            syncRepository.gate = CompletableDeferred()
            vm.onEvent(DevicesUiEvent.OnResumed)
            vm.onEvent(DevicesUiEvent.OnResumed)

            assertEquals(false, vm.uiState.value.isRefreshing)
            assertEquals(2, syncRepository.loads)
        }

    @Test
    fun `resuming does not show the refresh indicator`() = runTest(dispatcher) {
        syncRepository.devices = Result.Success(listOf(device(current = true)))
        val vm = viewModel()

        syncRepository.gate = CompletableDeferred()
        vm.onEvent(DevicesUiEvent.OnResumed)

        assertEquals(false, vm.uiState.value.isRefreshing)
    }

    @Test
    fun `pulling during a silent re-read joins it`() = runTest(dispatcher) {
        syncRepository.devices = Result.Success(listOf(device(current = true)))
        val vm = viewModel()

        syncRepository.gate = CompletableDeferred()
        vm.onEvent(DevicesUiEvent.OnResumed)
        vm.onEvent(DevicesUiEvent.OnRefresh)

        assertEquals(true, vm.uiState.value.isRefreshing)
        assertEquals(2, syncRepository.loads)

        syncRepository.gate?.complete(Unit)

        assertEquals(false, vm.uiState.value.isRefreshing)
    }

    @Test
    fun `pulling during the first load does not show the refresh indicator`() =
        runTest(dispatcher) {
            syncRepository.gate = CompletableDeferred()
            val vm = viewModel()

            vm.onEvent(DevicesUiEvent.OnRefresh)

            assertEquals(false, vm.uiState.value.isRefreshing)
            assertEquals(1, syncRepository.loads)
        }

    @Test
    fun `this device is split off from the others`() = runTest(dispatcher) {
        val current = device(current = true)
        val other = device()
        syncRepository.devices = Result.Success(listOf(other, current))

        val vm = viewModel()

        val state = assertIs<DevicesState.Loaded>(vm.uiState.value.devices)
        assertEquals(current, state.current?.device)
        assertEquals(listOf(other), state.others.map { it.device })
    }

    @Test
    fun `other devices are listed most recently seen first`() = runTest(dispatcher) {
        val older = device(lastSeenAt = 1_700_000_000)
        val newer = device(lastSeenAt = 1_800_000_000)
        syncRepository.devices = Result.Success(listOf(older, newer))

        val vm = viewModel()

        val state = assertIs<DevicesState.Loaded>(vm.uiState.value.devices)
        assertEquals(null, state.current)
        assertEquals(listOf(newer, older), state.others.map { it.device })
    }

    @Test
    fun `a failed load is shown as failed`() = runTest(dispatcher) {
        syncRepository.devices = Result.Failure(SyncError.Network(NetworkFailure.Timeout))

        val vm = viewModel()

        assertEquals(DevicesState.Failed, vm.uiState.value.devices)
    }

    @Test
    fun `retry loads the devices again`() = runTest(dispatcher) {
        syncRepository.devices = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        val vm = viewModel()

        val current = device(current = true)
        syncRepository.devices = Result.Success(listOf(current))
        vm.onEvent(DevicesUiEvent.OnRetryClicked)

        assertEquals(onlyThisDevice(current), vm.uiState.value.devices)
        assertEquals(2, syncRepository.loads)
    }

    @Test
    fun `resuming re-reads the list without going back to loading`() = runTest(dispatcher) {
        val first = device(current = true)
        syncRepository.devices = Result.Success(listOf(first))
        val vm = viewModel()

        val second = device(current = true)
        syncRepository.devices = Result.Success(listOf(second))
        syncRepository.gate = CompletableDeferred()
        vm.onEvent(DevicesUiEvent.OnResumed)

        assertEquals(onlyThisDevice(first), vm.uiState.value.devices)

        syncRepository.gate?.complete(Unit)

        assertEquals(onlyThisDevice(second), vm.uiState.value.devices)
    }

    @Test
    fun `a failed refresh keeps the list on screen`() = runTest(dispatcher) {
        val current = device(current = true)
        syncRepository.devices = Result.Success(listOf(current))
        val vm = viewModel()

        syncRepository.devices = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        vm.onEvent(DevicesUiEvent.OnResumed)

        assertEquals(onlyThisDevice(current), vm.uiState.value.devices)
        assertEquals(2, syncRepository.loads)
    }

    @Test
    fun `resuming after a failed load loads again`() = runTest(dispatcher) {
        syncRepository.devices = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        val vm = viewModel()

        val current = device(current = true)
        syncRepository.devices = Result.Success(listOf(current))
        vm.onEvent(DevicesUiEvent.OnResumed)

        assertEquals(onlyThisDevice(current), vm.uiState.value.devices)
    }

    @Test
    fun `retry while loading does not start a second load`() = runTest(dispatcher) {
        syncRepository.gate = CompletableDeferred()
        val vm = viewModel()

        vm.onEvent(DevicesUiEvent.OnRetryClicked)

        assertEquals(1, syncRepository.loads)
    }

    private class FakeSyncRepository : SyncRepository {
        var devices: Result<List<Device>, SyncError> = Result.Success(emptyList())
        var gate: CompletableDeferred<Unit>? = null
        var loads = 0

        override suspend fun devices(): Result<List<Device>, SyncError> {
            loads++
            gate?.await()
            return devices
        }

        override suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError> =
            error("not used")

        override suspend fun register(
            serverUrl: String,
            userId: UUID,
            username: String,
            password: String,
            invite: String?,
        ): Result<SyncAccount, SyncError> = error("not used")

        override suspend fun logout(): Result<Unit, SyncError> = error("not used")
    }

    private companion object {
        fun onlyThisDevice(device: Device) = DevicesState.Loaded(
            current = DeviceItem(device, DeviceActivity.ThisDevice),
            others = emptyList(),
        )

        fun device(
            current: Boolean = false,
            lastSeenAt: Long = 1_800_000_000,
        ) = Device(
            id = UUID.randomUUID(),
            name = "Pixel",
            platform = "android",
            appVersion = "2.2.0",
            createdAt = Instant.fromEpochSeconds(1_700_000_000),
            lastSeenAt = Instant.fromEpochSeconds(lastSeenAt),
            authTime = null,
            loggedIn = true,
            current = current,
        )
    }
}

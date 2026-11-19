package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck.Failed.Reason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectServerViewModelTest {

    private class FakeSyncRepository : SyncRepository {
        var result: Result<ServerInfo, SyncError> = Result.Success(info)
        val checked = mutableListOf<String>()

        override suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError> {
            checked += serverUrl
            return result
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSyncRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = ConnectServerViewModel(repository)
        .also { it.state.launchIn(backgroundScope) }

    private val ConnectServerViewModel.check
        get() = state.value.enterDomainState.check

    private fun TestScope.type(vm: ConnectServerViewModel, text: String) {
        vm.state.value.enterDomainState.addressTextFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun checks_the_normalised_origin_once_typing_pauses() = runTest(dispatcher) {
        val vm = viewModel()

        type(vm, "vault.example.org/")
        assertEquals(ServerCheck.Idle, vm.check)
        assertEquals(emptyList(), repository.checked)

        advanceUntilIdle()

        assertEquals(listOf("https://vault.example.org"), repository.checked)
        val found = assertIs<ServerCheck.Found>(vm.check)
        assertEquals("https://vault.example.org", found.serverUrl)
        assertEquals(true, found.inviteOnly)
        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun editing_keeps_the_previous_result_until_typing_pauses() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()

        type(vm, "vault.example.com")
        assertIs<ServerCheck.Found>(vm.check)
        assertEquals(listOf("https://vault.example.org"), repository.checked)

        advanceUntilIdle()
        assertEquals(listOf("https://vault.example.org", "https://vault.example.com"), repository.checked)
    }

    @Test
    fun a_scheme_set_without_the_field_is_kept() = runTest(dispatcher) {
        val vm = viewModel()

        // setText bypasses the field's InputTransformation, so the scheme stays in the text.
        type(vm, "http://localhost:8000")
        advanceUntilIdle()

        assertEquals(listOf("http://localhost:8000"), repository.checked)
    }

    @Test
    fun only_the_last_address_is_checked() = runTest(dispatcher) {
        val vm = viewModel()

        type(vm, "vault.ex")
        advanceTimeBy(100)
        type(vm, "vault.example.org")
        advanceUntilIdle()

        assertEquals(listOf("https://vault.example.org"), repository.checked)
    }

    @Test
    fun an_invalid_address_is_flagged_without_asking_the_server() = runTest(dispatcher) {
        val vm = viewModel()

        type(vm, "ftp://vault.example.org")
        assertEquals(ServerCheck.Idle, vm.check)
        advanceUntilIdle()

        assertEquals(ServerCheck.Failed(Reason.InvalidAddress), vm.check)
        assertEquals(emptyList(), repository.checked)
    }

    @Test
    fun clearing_the_field_returns_to_idle() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()

        type(vm, "")
        advanceUntilIdle()

        assertEquals(ServerCheck.Idle, vm.check)
    }

    @Test
    fun a_failure_reports_its_specific_reason() = runTest(dispatcher) {
        repository.result = Result.Failure(SyncError.Network(NetworkFailure.Timeout))
        val vm = viewModel()

        type(vm, "192.168.178.21:8000")
        advanceUntilIdle()

        assertEquals(ServerCheck.Failed(Reason.Timeout), vm.check)
    }

    @Test
    fun retry_checks_again_without_waiting() = runTest(dispatcher) {
        repository.result = Result.Failure(SyncError.Network(NetworkFailure.Refused))
        val vm = viewModel()
        type(vm, "localhost:8000")
        advanceUntilIdle()

        repository.result = Result.Success(info)
        vm.onEvent(ConnectServerUiEvent.Retry)
        runCurrent()

        assertIs<ServerCheck.Found>(vm.check)
        assertEquals(2, repository.checked.size)
    }

    private companion object {
        val info = ServerInfo(
            version = "0.4.0",
            publicUrl = "https://vault.example.org",
            allowSignups = false,
        )
    }
}

package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.ui.text.transformation.SchemeStrippingTransformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerStep
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck.Failed.Reason
import de.davis.keygo.feature.account.presentation.setup.model.UsernameCheck
import de.davis.keygo.rust.FakeUsernameValidator
import de.davis.keygo.rust.username.UsernameError
import de.davisalessandro.keygo.rust.UsernameException
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
    private val usernameValidator = FakeUsernameValidator()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = ConnectServerViewModel(repository, usernameValidator)
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
    fun editing_drops_the_previous_result_at_once() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()

        type(vm, "vault.example.com")
        assertEquals(ServerCheck.Idle, vm.check)
        assertEquals(false, vm.state.value.canContinue)
        assertEquals(listOf("https://vault.example.org"), repository.checked)

        advanceUntilIdle()
        assertEquals(listOf("https://vault.example.org", "https://vault.example.com"), repository.checked)
    }

    @Test
    fun continue_is_ignored_while_the_new_address_waits() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()

        type(vm, "vault.example.com")
        vm.onEvent(ConnectServerUiEvent.Continue)

        assertEquals(ConnectServerStep.EnterDomain, vm.state.value.step)
    }

    @Test
    fun an_edit_that_keeps_the_origin_keeps_the_result() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()

        type(vm, "vault.example.org/")
        assertIs<ServerCheck.Found>(vm.check)
        advanceUntilIdle()
        assertEquals(listOf("https://vault.example.org"), repository.checked)
    }

    @Test
    fun a_scheme_change_alone_checks_again() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "localhost:8000")
        advanceUntilIdle()

        vm.onEvent(ConnectServerUiEvent.SchemeChanged("http://"))
        runCurrent()
        assertEquals(ServerCheck.Idle, vm.check)

        advanceUntilIdle()
        assertEquals(listOf("https://localhost:8000", "http://localhost:8000"), repository.checked)
    }

    /**
     * Types like the field does: the edit runs through the same transformation EnterDomainContent
     * remembers, which reports the stripped scheme back as [ConnectServerUiEvent.SchemeChanged].
     */
    private fun TestScope.typeInField(vm: ConnectServerViewModel, text: String) {
        val transformation = SchemeStrippingTransformation {
            vm.onEvent(ConnectServerUiEvent.SchemeChanged(it))
        }
        vm.state.value.enterDomainState.addressTextFieldState.edit {
            replace(0, length, text)
            with(transformation) { transformInput() }
        }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun a_scheme_typed_into_the_field_is_shown_and_checked() = runTest(dispatcher) {
        val vm = viewModel()

        typeInField(vm, "http://localhost:8000")
        val enterDomain = vm.state.value.enterDomainState
        assertEquals("localhost:8000", enterDomain.addressTextFieldState.text.toString())
        assertEquals("http://", enterDomain.typedScheme)

        advanceUntilIdle()
        assertEquals(listOf("http://localhost:8000"), repository.checked)
    }

    @Test
    fun clearing_the_field_forgets_the_typed_scheme() = runTest(dispatcher) {
        val vm = viewModel()
        typeInField(vm, "http://localhost:8000")

        typeInField(vm, "")
        assertEquals(null, vm.state.value.enterDomainState.typedScheme)

        typeInField(vm, "vault.example.org")
        advanceUntilIdle()
        assertEquals(listOf("https://vault.example.org"), repository.checked)
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

    private val ConnectServerViewModel.usernameCheck
        get() = state.value.chooseUsernameState.usernameCheck

    private fun TestScope.typeUsername(vm: ConnectServerViewModel, text: String) {
        vm.state.value.chooseUsernameState.usernameTextFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun a_valid_username_counts_at_once_in_its_normalized_form() = runTest(dispatcher) {
        val vm = viewModel()

        typeUsername(vm, "Ada")

        assertEquals(UsernameCheck.Valid("ada"), vm.usernameCheck)
    }

    @Test
    fun an_invalid_username_is_flagged_once_typing_pauses() = runTest(dispatcher) {
        usernameValidator.errors = mapOf("ad" to UsernameException.TooShort(3u))
        val vm = viewModel()

        typeUsername(vm, "ad")
        assertEquals(UsernameCheck.Idle, vm.usernameCheck)
        assertEquals(false, vm.state.value.chooseUsernameState.canContinue)

        advanceUntilIdle()
        assertEquals(
            UsernameCheck.Invalid(UsernameError.TooShort(3)),
            vm.usernameCheck,
        )
    }

    @Test
    fun fixing_the_username_drops_the_error_at_once() = runTest(dispatcher) {
        usernameValidator.errors = mapOf("admin" to UsernameException.Reserved())
        val vm = viewModel()
        typeUsername(vm, "admin")
        advanceUntilIdle()
        assertEquals(UsernameCheck.Invalid(UsernameError.Reserved), vm.usernameCheck)

        typeUsername(vm, "admin2")

        assertEquals(UsernameCheck.Valid("admin2"), vm.usernameCheck)
    }

    @Test
    fun an_empty_username_stays_idle() = runTest(dispatcher) {
        usernameValidator.errors = mapOf("" to UsernameException.TooShort(3u))
        val vm = viewModel()
        typeUsername(vm, "ada")

        typeUsername(vm, "")
        advanceUntilIdle()

        assertEquals(UsernameCheck.Idle, vm.usernameCheck)
    }

    @Test
    fun the_allowed_characters_come_from_the_validator() = runTest(dispatcher) {
        usernameValidator.allowedCharsResult = listOf("a-z", "_")
        val vm = viewModel()

        assertEquals(listOf("a-z", "_"), vm.state.value.chooseUsernameState.allowedUsernameChars)
    }

    @Test
    fun continue_needs_a_valid_username_and_an_invite() = runTest(dispatcher) {
        val vm = viewModel()
        typeUsername(vm, "ada")
        assertEquals(false, vm.state.value.chooseUsernameState.canContinue)

        vm.state.value.chooseUsernameState.inviteTextFieldState
            .setTextAndPlaceCursorAtEnd("K7QM2XRA9F3T")
        Snapshot.sendApplyNotifications()
        runCurrent()

        assertEquals(true, vm.state.value.chooseUsernameState.canContinue)
    }

    private companion object {
        val info = ServerInfo(
            version = "0.4.0",
            publicUrl = "https://vault.example.org",
            allowSignups = false,
        )
    }
}

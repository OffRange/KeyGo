package de.davis.keygo.feature.account.presentation.setup

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.usecase.VerifyPasswordUseCase
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.sync.domain.model.Device
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.sync.domain.usecase.RegisterUseCase
import de.davis.keygo.core.ui.text.transformation.SchemeStrippingTransformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerEvent
import de.davis.keygo.feature.account.presentation.setup.model.AccountChoice
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerStep
import de.davis.keygo.feature.account.presentation.setup.model.ConnectServerUiEvent
import de.davis.keygo.feature.account.presentation.setup.model.PasswordCheck
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck
import de.davis.keygo.feature.account.presentation.setup.model.SignInCheck
import de.davis.keygo.feature.account.presentation.setup.model.UsernameCheck
import de.davis.keygo.rust.FakeUsernameValidator
import de.davis.keygo.rust.username.UsernameError
import de.davisalessandro.keygo.rust.UsernameException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectServerViewModelTest {

    private class FakeSyncRepository : SyncRepository {
        data class Login(val serverUrl: String, val username: String, val password: String)

        var result: Result<ServerInfo, SyncError> = Result.Success(info)

        /** When set, register and login suspend until it completes. */
        var gate: CompletableDeferred<Unit>? = null
        val checked = mutableListOf<String>()

        override suspend fun checkServer(serverUrl: String): Result<ServerInfo, SyncError> {
            checked += serverUrl
            return result
        }

        override suspend fun register(
            serverUrl: String,
            userId: UUID,
            username: String,
            password: String,
            invite: String?,
        ): Result<SyncAccount, SyncError> {
            registered += serverUrl
            gate?.await()
            return registerFailure?.let { Result.Failure(it) }
                ?: Result.Success(SyncAccount(userId, username, UUID.randomUUID()))
        }

        var registerFailure: SyncError? = null
        val registered = mutableListOf<String>()

        override suspend fun login(
            serverUrl: String,
            username: String,
            password: String,
        ): Result<SyncAccount, SyncError> {
            loggedIn += Login(serverUrl, username, password)
            gate?.await()
            return loginFailure?.let { Result.Failure(it) }
                ?: Result.Success(SyncAccount(UUID.randomUUID(), username, UUID.randomUUID()))
        }

        var loginFailure: SyncError? = null
        val loggedIn = mutableListOf<Login>()

        override suspend fun logout(): Result<Unit, SyncError> = Result.Success(Unit)

        override suspend fun devices(): Result<List<Device>, SyncError> = error("not used")

        override suspend fun deleteDevice(deviceId: UUID): Result<Unit, SyncError> =
            error("not used")

        override suspend fun stepUp(password: String): Result<Unit, SyncError> = error("not used")
    }

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSyncRepository()
    private val usernameValidator = FakeUsernameValidator()
    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun unobservedViewModel() = ConnectServerViewModel(
        syncRepository = repository,
        usernameValidator = usernameValidator,
        register = RegisterUseCase(
            syncRepository = repository,
            accountRepository = accountRepository,
            verifyPassword = VerifyPasswordUseCase(session, accountRepository),
        ),
        session = session,
    )

    private fun TestScope.viewModel() =
        unobservedViewModel().also { it.state.launchIn(backgroundScope) }

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
        assertEquals("https://vault.example.org", assertIs<ServerCheck.Found>(vm.check).serverUrl)
        assertEquals(false, vm.state.value.canContinue)
        assertEquals(listOf("https://vault.example.org"), repository.checked)

        advanceUntilIdle()
        assertEquals(
            listOf("https://vault.example.org", "https://vault.example.com"),
            repository.checked
        )
    }

    @Test
    fun continue_moves_on_once_the_address_is_found() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()

        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()

        assertEquals(ConnectServerStep.SignInOrOut, vm.state.value.step)
    }

    @Test
    fun continue_is_ignored_while_the_address_is_invalid() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "ftp://vault.example.org")
        advanceUntilIdle()

        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()

        assertEquals(ConnectServerStep.EnterDomain, vm.state.value.step)
    }

    @Test
    fun continue_is_ignored_while_the_new_address_waits() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()

        type(vm, "vault.example.com")
        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()

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
        // The https result is still shown, but it is not for this origin.
        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()
        assertEquals(ConnectServerStep.EnterDomain, vm.state.value.step)

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

        assertEquals(ServerCheck.InvalidAddress, vm.check)
        assertEquals(emptyList(), repository.checked)
    }

    @Test
    fun an_address_with_a_path_is_flagged_without_asking_the_server() = runTest(dispatcher) {
        val vm = viewModel()

        type(vm, "vault.example.org/keygo")
        advanceUntilIdle()

        assertEquals(ServerCheck.NotAnOrigin, vm.check)
        assertEquals(emptyList(), repository.checked)
        assertEquals(false, vm.state.value.canContinue)
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

        assertEquals(ServerCheck.Failed(SyncError.Network(NetworkFailure.Timeout)), vm.check)
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

    private fun TestScope.typeInvite(vm: ConnectServerViewModel, text: String) {
        vm.state.value.chooseUsernameState.inviteTextFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.chooseUsernameStep(): ConnectServerViewModel {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()
        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()
        assertEquals(ConnectServerStep.SignInOrOut, vm.state.value.step)
        vm.onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.CreateAccount))
        runCurrent()
        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
        return vm
    }

    private fun TestScope.signInOrOutStep(): ConnectServerViewModel {
        val vm = viewModel()
        type(vm, "vault.example.org")
        advanceUntilIdle()
        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()
        assertEquals(ConnectServerStep.SignInOrOut, vm.state.value.step)
        return vm
    }

    @Test
    fun the_account_step_cannot_be_skipped_with_continue() = runTest(dispatcher) {
        val vm = signInOrOutStep()
        assertEquals(false, vm.state.value.canContinue)

        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()
        assertEquals(ConnectServerStep.SignInOrOut, vm.state.value.step)
    }

    @Test
    fun choosing_to_create_an_account_moves_on_to_the_username() = runTest(dispatcher) {
        val vm = signInOrOutStep()

        vm.onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.CreateAccount))
        runCurrent()
        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
    }

    @Test
    fun choosing_to_sign_in_moves_on_to_the_sign_in_step() = runTest(dispatcher) {
        val vm = signInOrOutStep()

        vm.onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.SignIn))
        runCurrent()

        assertEquals(ConnectServerStep.SignIn, vm.state.value.step)
        assertEquals(
            listOf(
                ConnectServerStep.EnterDomain,
                ConnectServerStep.SignInOrOut,
                ConnectServerStep.SignIn,
            ),
            vm.state.value.steps,
        )
    }

    @Test
    fun back_from_sign_in_returns_to_the_choice_and_keeps_its_path() = runTest(dispatcher) {
        val vm = signInStep()

        vm.onEvent(ConnectServerUiEvent.Back)
        runCurrent()

        assertEquals(ConnectServerStep.SignInOrOut, vm.state.value.step)
        assertEquals(3, vm.state.value.steps.size)
    }

    @Test
    fun switching_to_create_account_after_sign_in_takes_the_longer_path() = runTest(dispatcher) {
        val vm = signInStep()
        vm.onEvent(ConnectServerUiEvent.Back)
        runCurrent()

        vm.onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.CreateAccount))
        runCurrent()

        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
        assertEquals(4, vm.state.value.steps.size)
    }

    @Test
    fun sign_in_continue_needs_a_username_and_a_password() = runTest(dispatcher) {
        val vm = signInStep()
        assertEquals(false, vm.state.value.canContinue)

        typeSignInUsername(vm, "ada")
        assertEquals(false, vm.state.value.canContinue)

        typeSignInPassword(vm, "secret")
        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun signing_in_logs_in_on_the_checked_origin() = runTest(dispatcher) {
        val vm = signInStep()
        typeSignInUsername(vm, "ada")
        typeSignInPassword(vm, "secret")

        vm.onEvent(ConnectServerUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(
            listOf(FakeSyncRepository.Login("https://vault.example.org", "ada", "secret")),
            repository.loggedIn,
        )
        assertEquals(emptyList(), repository.registered)
        assertEquals(ConnectServerEvent.SignedIn, vm.event.first())
        assertEquals("", vm.state.value.signInState.passwordTextFieldState.text.toString())
    }

    @Test
    fun a_rejected_sign_in_stays_on_its_step_with_the_error() = runTest(dispatcher) {
        repository.loginFailure = SyncError.InvalidCredentials
        val vm = signInStep()
        typeSignInUsername(vm, "ada")
        typeSignInPassword(vm, "wrong")

        vm.onEvent(ConnectServerUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(ConnectServerStep.SignIn, vm.state.value.step)
        assertEquals(
            SignInCheck.Failed(SyncError.InvalidCredentials),
            vm.state.value.signInState.check,
        )
        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun locking_the_session_clears_the_sign_in_password() = runTest(dispatcher) {
        seedAccount("secret")
        val vm = signInStep()
        typeSignInPassword(vm, "secret")

        session.endSession()
        runCurrent()

        assertEquals("", vm.state.value.signInState.passwordTextFieldState.text.toString())
    }

    private fun TestScope.signInStep(): ConnectServerViewModel {
        val vm = signInOrOutStep()
        vm.onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.SignIn))
        runCurrent()
        assertEquals(ConnectServerStep.SignIn, vm.state.value.step)
        return vm
    }

    private fun TestScope.typeSignInUsername(vm: ConnectServerViewModel, text: String) {
        vm.state.value.signInState.usernameTextFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.typeSignInPassword(vm: ConnectServerViewModel, text: String) {
        vm.state.value.signInState.passwordTextFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun an_account_choice_off_its_step_is_ignored() = runTest(dispatcher) {
        val vm = chooseUsernameStep()

        vm.onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.CreateAccount))
        runCurrent()
        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
    }

    @Test
    fun a_valid_username_counts_once_typing_pauses_in_its_normalized_form() = runTest(dispatcher) {
        val vm = viewModel()

        typeUsername(vm, "Ada")
        assertEquals(UsernameCheck.Idle, vm.usernameCheck)

        advanceUntilIdle()
        assertEquals(UsernameCheck.Valid("ada"), vm.usernameCheck)
    }

    @Test
    fun an_invalid_username_is_flagged_once_typing_pauses() = runTest(dispatcher) {
        usernameValidator.errors = mapOf("ad" to UsernameException.TooShort(3u))
        val vm = chooseUsernameStep()
        typeInvite(vm, "K7QM2XRA9F3T")

        typeUsername(vm, "ad")
        assertEquals(UsernameCheck.Idle, vm.usernameCheck)
        assertEquals(false, vm.state.value.canContinue)

        advanceUntilIdle()
        assertEquals(
            UsernameCheck.Invalid(UsernameError.TooShort(3)),
            vm.usernameCheck,
        )
    }

    @Test
    fun fixing_the_username_drops_the_error_once_typing_pauses() = runTest(dispatcher) {
        usernameValidator.errors = mapOf("admin" to UsernameException.Reserved())
        val vm = viewModel()
        typeUsername(vm, "admin")
        advanceUntilIdle()
        assertEquals(UsernameCheck.Invalid(UsernameError.Reserved), vm.usernameCheck)

        typeUsername(vm, "admin2")
        assertEquals(UsernameCheck.Invalid(UsernameError.Reserved), vm.usernameCheck)

        advanceUntilIdle()
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
    fun returning_from_the_background_keeps_the_checks() = runTest(dispatcher) {
        val vm = unobservedViewModel()
        val screen = vm.state.launchIn(backgroundScope)
        type(vm, "vault.example.org")
        advanceUntilIdle()
        typeUsername(vm, "ada")
        advanceUntilIdle()

        screen.cancel()
        advanceTimeBy(10_000)
        vm.state.launchIn(backgroundScope)
        runCurrent()

        assertEquals(true, vm.state.value.chooseUsernameState.serverInviteRequired)
        assertIs<UsernameCheck.Valid>(vm.usernameCheck)
        assertEquals(1, repository.checked.size)
    }

    @Test
    fun continue_needs_a_valid_username_and_an_invite() = runTest(dispatcher) {
        val vm = chooseUsernameStep()
        typeUsername(vm, "ada")
        assertEquals(false, vm.state.value.canContinue)

        typeInvite(vm, "K7QM2XRA9F3T")

        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun an_open_server_needs_no_invite() = runTest(dispatcher) {
        repository.result = Result.Success(info.copy(allowSignups = true))
        val vm = chooseUsernameStep()

        typeUsername(vm, "ada")

        assertEquals(false, vm.state.value.chooseUsernameState.serverInviteRequired)
        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun a_valid_username_enables_continue_before_typing_pauses() = runTest(dispatcher) {
        val vm = chooseUsernameStep()
        typeInvite(vm, "K7QM2XRA9F3T")

        typeUsername(vm, "ada")

        assertEquals(UsernameCheck.Idle, vm.usernameCheck)
        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun continue_follows_the_username_in_the_field_not_the_shown_check() = runTest(dispatcher) {
        usernameValidator.errors = mapOf("ad" to UsernameException.TooShort(3u))
        val vm = chooseUsernameStep()
        typeInvite(vm, "K7QM2XRA9F3T")
        typeUsername(vm, "ada")
        advanceUntilIdle()

        typeUsername(vm, "ad")

        assertEquals(UsernameCheck.Valid("ada"), vm.usernameCheck)
        assertEquals(false, vm.state.value.canContinue)
    }

    private suspend fun seedAccount(password: String) {
        val created = checkNotNull(session.createAccount(password).getOrNull())
        accountRepository.seed(
            Account(
                id = created.userId,
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(
                    key = created.passwordWrappedArk.ciphertext,
                    keyIV = created.passwordWrappedArk.nonce,
                    salt = created.salt,
                ),
                biometricWrappedArk = null,
            ),
        )
    }

    private val ConnectServerViewModel.passwordCheck
        get() = state.value.confirmPasswordState.check

    private fun TestScope.typePassword(vm: ConnectServerViewModel, text: String) {
        vm.state.value.confirmPasswordState.passwordTextFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private suspend fun TestScope.confirmPasswordStep(): ConnectServerViewModel {
        seedAccount("secret")
        val vm = chooseUsernameStep()
        typeInvite(vm, "K7QM2XRA9F3T")
        typeUsername(vm, "ada")
        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()
        assertEquals(ConnectServerStep.ConfirmPassword, vm.state.value.step)
        return vm
    }

    @Test
    fun continue_needs_a_password() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        assertEquals(false, vm.state.value.canContinue)

        typePassword(vm, "secret")

        assertEquals(true, vm.state.value.canContinue)
    }

    @Test
    fun the_unlock_password_passes_the_check() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        typePassword(vm, "secret")

        vm.onEvent(ConnectServerUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(PasswordCheck.Idle, vm.passwordCheck)
    }

    @Test
    fun a_wrong_password_is_flagged_as_incorrect() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        typePassword(vm, "wrong")

        vm.onEvent(ConnectServerUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(PasswordCheck.Incorrect, vm.passwordCheck)
    }

    @Test
    fun a_failed_derivation_is_reported_apart_from_a_wrong_password() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        typePassword(vm, "secret")
        session.failDerivation = true

        vm.onEvent(ConnectServerUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(PasswordCheck.Failed, vm.passwordCheck)
    }

    @Test
    fun locking_the_session_clears_the_password() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        typePassword(vm, "secret")

        session.endSession()
        runCurrent()

        assertEquals("", vm.state.value.confirmPasswordState.passwordTextFieldState.text.toString())
    }

    private suspend fun TestScope.submit(vm: ConnectServerViewModel) {
        typePassword(vm, "secret")
        vm.onEvent(ConnectServerUiEvent.Continue)
        advanceUntilIdle()
    }

    @Test
    fun a_confirmed_password_registers_on_the_checked_origin() = runTest(dispatcher) {
        val vm = confirmPasswordStep()

        submit(vm)

        assertEquals(listOf("https://vault.example.org"), repository.registered)
        assertEquals(ConnectServerEvent.AccountCreated, vm.event.first())
    }

    @Test
    fun a_wrong_password_never_reaches_the_server() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        typePassword(vm, "wrong")

        vm.onEvent(ConnectServerUiEvent.Continue)
        advanceUntilIdle()

        assertEquals(emptyList(), repository.registered)
    }

    @Test
    fun a_taken_username_sends_the_user_back_to_pick_another() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        repository.registerFailure = SyncError.UsernameTaken

        submit(vm)

        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
        assertEquals(UsernameCheck.Rejected(SyncError.UsernameTaken), vm.usernameCheck)
        assertEquals(false, vm.state.value.canContinue)

        typeUsername(vm, "grace")
        advanceUntilIdle()

        assertEquals(UsernameCheck.Valid("grace"), vm.usernameCheck)
        assertEquals(true, vm.state.value.canContinue)
    }

    private fun TestScope.back(vm: ConnectServerViewModel, times: Int = 1) {
        repeat(times) { vm.onEvent(ConnectServerUiEvent.Back) }
        runCurrent()
    }

    @Test
    fun a_rejection_is_dropped_once_back_on_the_address_step() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        repository.registerFailure = SyncError.UsernameTaken
        submit(vm)
        assertEquals(UsernameCheck.Rejected(SyncError.UsernameTaken), vm.usernameCheck)

        back(vm, times = 2)
        assertEquals(ConnectServerStep.EnterDomain, vm.state.value.step)

        val username = vm.state.value.chooseUsernameState.usernameTextFieldState.text.toString()
        assertEquals("ada", username)
        assertEquals(UsernameCheck.Valid("ada"), vm.usernameCheck)
    }

    @Test
    fun a_rejected_invite_is_dropped_once_back_on_the_address_step() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        repository.registerFailure = SyncError.InviteInvalid
        submit(vm)
        assertEquals(SyncError.InviteInvalid, vm.state.value.chooseUsernameState.inviteError)

        back(vm, times = 2)

        assertEquals(null, vm.state.value.chooseUsernameState.inviteError)
    }

    @Test
    fun a_rejection_survives_going_back_to_the_account_choice() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        repository.registerFailure = SyncError.UsernameTaken
        submit(vm)

        back(vm)
        assertEquals(ConnectServerStep.SignInOrOut, vm.state.value.step)

        assertEquals(UsernameCheck.Rejected(SyncError.UsernameTaken), vm.usernameCheck)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun back_from_the_password_step_clears_the_password() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        typePassword(vm, "secret")

        back(vm)

        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
        val password = vm.state.value.confirmPasswordState.passwordTextFieldState
        assertEquals("", password.text.toString())
        assertEquals(false, password.undoState.canUndo)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun back_from_sign_in_clears_the_password() = runTest(dispatcher) {
        val vm = signInStep()
        typeSignInUsername(vm, "ada")
        typeSignInPassword(vm, "secret")

        back(vm)

        val signIn = vm.state.value.signInState
        assertEquals("ada", signIn.usernameTextFieldState.text.toString())
        assertEquals("", signIn.passwordTextFieldState.text.toString())
        assertEquals(false, signIn.passwordTextFieldState.undoState.canUndo)
    }

    @Test
    fun a_rejection_bounce_keeps_the_password() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        repository.registerFailure = SyncError.UsernameTaken

        submit(vm)

        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
        val password = vm.state.value.confirmPasswordState.passwordTextFieldState
        assertEquals("secret", password.text.toString())
    }

    @Test
    fun a_spent_invite_is_flagged_on_the_invite_field() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        repository.registerFailure = SyncError.InviteInvalid

        submit(vm)

        assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
        assertEquals(SyncError.InviteInvalid, vm.state.value.chooseUsernameState.inviteError)
        assertEquals(false, vm.state.value.canContinue)
    }

    @Test
    fun a_network_failure_on_register_stays_on_the_password_step() = runTest(dispatcher) {
        val vm = confirmPasswordStep()
        repository.registerFailure = SyncError.Network(NetworkFailure.Timeout)

        submit(vm)

        assertEquals(ConnectServerStep.ConfirmPassword, vm.state.value.step)
        assertEquals(PasswordCheck.RegisterFailed(SyncError.Network(NetworkFailure.Timeout)), vm.passwordCheck)
    }

    private companion object {
        val info = ServerInfo(
            version = "0.4.0",
            publicUrl = "https://vault.example.org",
            allowSignups = false,
        )
    }

    @Test
    fun back_is_ignored_while_signing_in() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val vm = signInStep()
        typeSignInUsername(vm, "ada")
        typeSignInPassword(vm, "secret")
        vm.onEvent(ConnectServerUiEvent.Continue)
        runCurrent()
        assertEquals(SignInCheck.Checking, vm.state.value.signInState.check)

        vm.onEvent(ConnectServerUiEvent.Back)
        runCurrent()
        assertEquals(ConnectServerStep.SignIn, vm.state.value.step)

        repository.gate?.complete(Unit)
        runCurrent()
        vm.onEvent(ConnectServerUiEvent.Back)
        runCurrent()
        assertEquals(ConnectServerStep.SignInOrOut, vm.state.value.step)
    }

    @Test
    fun a_late_register_rejection_cannot_strand_the_wizard_on_the_sign_in_path() =
        runTest(dispatcher) {
            repository.gate = CompletableDeferred()
            repository.registerFailure = SyncError.UsernameTaken
            val vm = confirmPasswordStep()
            typePassword(vm, "secret")
            vm.onEvent(ConnectServerUiEvent.Continue)
            advanceUntilIdle()
            assertEquals(listOf("https://vault.example.org"), repository.registered)

            // Back to the choice and over to sign in, while the register still runs.
            vm.onEvent(ConnectServerUiEvent.Back)
            vm.onEvent(ConnectServerUiEvent.Back)
            vm.onEvent(ConnectServerUiEvent.ChooseAccount(AccountChoice.SignIn))
            runCurrent()
            assertEquals(ConnectServerStep.ConfirmPassword, vm.state.value.step)

            repository.gate?.complete(Unit)
            runCurrent()

            assertEquals(ConnectServerStep.ChooseUsername, vm.state.value.step)
            assertTrue(vm.state.value.step in vm.state.value.steps)
        }
}

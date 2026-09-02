package de.davis.keygo.app.presentation

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.legacy_migration.FakeMainPasswordRepository
import de.davis.keygo.legacy_migration.hasMainPasswordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(session: FakeSession): AppViewModel = AppViewModel(
        accountRepository = FakeAccountRepository(),
        hasV1Password = hasMainPasswordUseCase(FakeMainPasswordRepository()),
        session = session,
    ).also { it.isLocked.launchIn(backgroundScope) }

    @Test
    fun `isLocked stays false through a cold start that never logs in`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        advanceUntilIdle()

        assertFalse(vm.isLocked.value)
    }

    @Test
    fun `isLocked turns true only after an active session ends`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        // isLocked's collector must be subscribed (and so already see isActive = false) before
        // the first mutation - StandardTestDispatcher defers launchIn's collection until this
        // point, so starting the session any earlier would be missed rather than seen as a
        // false -> true transition.
        advanceUntilIdle()

        session.startSession(ByteArray(32))
        advanceUntilIdle()
        assertFalse(vm.isLocked.value)

        session.endSession()
        advanceUntilIdle()
        assertTrue(vm.isLocked.value)
    }

    @Test
    fun `isLocked returns to false once the session is reinitialized`() = runTest(dispatcher) {
        val session = FakeSession()
        val vm = viewModel(session)
        advanceUntilIdle()

        session.startSession(ByteArray(32))
        advanceUntilIdle()
        session.endSession()
        advanceUntilIdle()
        assertTrue(vm.isLocked.value)

        session.startSession(ByteArray(32))
        advanceUntilIdle()

        assertFalse(vm.isLocked.value)
    }
}

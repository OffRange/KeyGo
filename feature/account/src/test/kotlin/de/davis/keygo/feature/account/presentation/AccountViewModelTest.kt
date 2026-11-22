package de.davis.keygo.feature.account.presentation

import de.davis.keygo.core.sync.FakeServerLinkRepository
import de.davis.keygo.core.sync.domain.model.ServerLink
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

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeServerLinkRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AccountViewModel(repository)

    @Test
    fun `without a link the state is none`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        assertEquals(ServerLink.None, vm.uiState.value)
    }

    @Test
    fun `a new link is shown as connected`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        val link = ServerLink.Connected(UUID.randomUUID(), "https://vault.example.org/", "ada")
        repository.link.value = link

        assertEquals(link, vm.uiState.value)
    }

    @Test
    fun `a removed link switches back to none`() = runTest(dispatcher) {
        repository.link.value =
            ServerLink.Connected(UUID.randomUUID(), "https://vault.example.org/", "ada")
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)
        assertIs<ServerLink.Connected>(vm.uiState.value)

        repository.link.value = ServerLink.None

        assertEquals(ServerLink.None, vm.uiState.value)
    }
}

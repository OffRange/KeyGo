package de.davis.keygo.feature.account.presentation

import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.core.sync.domain.repository.ServerLinkRepository
import de.davis.keygo.feature.account.presentation.models.AccountState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private class FakeServerLinkRepository : ServerLinkRepository {
        val link = MutableStateFlow<ServerLink?>(null)

        override fun observeLink(): Flow<ServerLink?> = link
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeServerLinkRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AccountViewModel(repository)

    @Test
    fun `without a link the account is local`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        assertEquals(AccountState.Local, vm.uiState.value)
    }

    @Test
    fun `a new link switches the account to connected`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)

        repository.link.value = ServerLink(UUID.randomUUID(), "https://vault.example.org/", "ada")

        assertEquals(
            AccountState.Connected(username = "ada", server = "vault.example.org"),
            vm.uiState.value,
        )
    }

    @Test
    fun `a removed link switches the account back to local`() = runTest(dispatcher) {
        repository.link.value = ServerLink(UUID.randomUUID(), "https://vault.example.org/", "ada")
        val vm = viewModel()
        vm.uiState.launchIn(backgroundScope)
        assertIs<AccountState.Connected>(vm.uiState.value)

        repository.link.value = null

        assertEquals(AccountState.Local, vm.uiState.value)
    }
}

package de.davis.keygo.feature.password_health.data.repository

import de.davis.keygo.feature.password_health.data.local.model.ProtoBreachCheckState
import de.davis.keygo.feature.password_health.domain.model.BreachCheckState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BreachCheckStateRepositoryImplTest {

    private val dataStore = FakeDataStore(ProtoBreachCheckState.getDefaultInstance())
    private val repository = BreachCheckStateRepositoryImpl(dataStore)

    @Test
    fun theBreachCheckIsOffUntilTurnedOn() = runTest {
        assertEquals(BreachCheckState(enabled = false), repository.getBreachCheckState())
    }

    @Test
    fun turningItOnIsRemembered() = runTest {
        repository.setBreachEnabled(true)

        assertEquals(BreachCheckState(enabled = true), repository.getBreachCheckState())
        assertEquals(true, dataStore.state.value.enabled)
    }

    @Test
    fun turningItOffAgainIsRemembered() = runTest {
        repository.setBreachEnabled(true)
        repository.setBreachEnabled(false)

        assertEquals(BreachCheckState(enabled = false), repository.getBreachCheckState())
    }

    @Test
    fun observersSeeEveryChange() = runTest {
        val seen = mutableListOf<BreachCheckState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeBreachCheckState().take(3).toList(seen)
        }

        repository.setBreachEnabled(true)
        repository.setBreachEnabled(false)

        assertEquals(
            listOf(false, true, false).map { BreachCheckState(enabled = it) },
            seen,
        )
    }

    @Test
    fun observingStartsFromTheStoredValue() = runTest {
        dataStore.state.value = ProtoBreachCheckState.newBuilder().setEnabled(true).build()

        assertEquals(BreachCheckState(enabled = true), repository.observeBreachCheckState().first())
    }
}

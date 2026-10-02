package de.davis.keygo.feature.password_health.data.repository

import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthSettings
import de.davis.keygo.feature.password_health.domain.model.HealthSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthSettingsRepositoryImplTest {

    private val dataStore = FakeDataStore(ProtoHealthSettings.getDefaultInstance())
    private val repository = HealthSettingsRepositoryImpl(dataStore)

    @Test
    fun everythingIsOffUntilTurnedOn() = runTest {
        assertEquals(settings(), repository.getBreachCheckState())
    }

    @Test
    fun turningTheBreachCheckOnIsRemembered() = runTest {
        repository.setBreachEnabled(true)

        assertEquals(settings(breaches = true), repository.getBreachCheckState())
        assertEquals(true, dataStore.state.value.breachesEnabled)
    }

    @Test
    fun turningTheBreachCheckOffAgainIsRemembered() = runTest {
        repository.setBreachEnabled(true)
        repository.setBreachEnabled(false)

        assertEquals(settings(), repository.getBreachCheckState())
    }

    @Test
    fun turningNotificationsOnIsRemembered() = runTest {
        repository.setNotificationEnabled(true)

        assertEquals(settings(notifications = true), repository.getBreachCheckState())
        assertEquals(true, dataStore.state.value.notificationsEnabled)
    }

    @Test
    fun eachSettingLeavesTheOtherAlone() = runTest {
        repository.setBreachEnabled(true)
        repository.setNotificationEnabled(true)
        repository.setBreachEnabled(false)

        assertEquals(settings(notifications = true), repository.getBreachCheckState())
    }

    @Test
    fun observersSeeEveryChange() = runTest {
        val seen = mutableListOf<HealthSettings>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeBreachCheckState().take(3).toList(seen)
        }

        repository.setBreachEnabled(true)
        repository.setBreachEnabled(false)

        assertEquals(
            listOf(false, true, false).map { settings(breaches = it) },
            seen,
        )
    }

    @Test
    fun observingStartsFromTheStoredValue() = runTest {
        dataStore.state.value = ProtoHealthSettings.newBuilder().setBreachesEnabled(true).build()

        assertEquals(settings(breaches = true), repository.observeBreachCheckState().first())
    }

    private fun settings(breaches: Boolean = false, notifications: Boolean = false) =
        HealthSettings(breachesEnabled = breaches, notificationsEnabled = notifications)
}

package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.feature.password_health.FakeHealthCheckNotifierScheduler
import de.davis.keygo.feature.password_health.FakeHealthNotificationStateRepository
import de.davis.keygo.feature.password_health.FakeHealthSettingsRepository
import de.davis.keygo.feature.password_health.FakeNotifier
import de.davis.keygo.feature.password_health.domain.model.HealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.model.KeyGoNotification
import de.davis.keygo.feature.password_health.domain.model.VaultFingerprint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetHealthNotificationsUseCaseTest {

    private val settings = FakeHealthSettingsRepository(notificationsEnabled = false)
    private val state = FakeHealthNotificationStateRepository(
        HealthNotificationState(snapshot = HealthSnapshot(3, 0, VaultFingerprint(byteArrayOf(1)))),
    )
    private val scheduler = FakeHealthCheckNotifierScheduler()
    private val notifier = FakeNotifier()

    private val useCase = SetHealthNotificationsUseCase(settings, state, scheduler, notifier)

    @Test
    fun turningOnSchedulesTheReminder() = runTest {
        useCase(true)

        assertTrue(settings.state.value.notificationsEnabled)
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun turningOffCancelsAndForgetsEverything() = runTest {
        useCase(true)

        useCase(false)

        assertFalse(settings.state.value.notificationsEnabled)
        assertFalse(scheduler.scheduled)
        assertEquals(HealthNotificationState(), state.state)
        assertEquals(listOf(KeyGoNotification.Kind.NeedsAttention), notifier.cancelled)
    }

}

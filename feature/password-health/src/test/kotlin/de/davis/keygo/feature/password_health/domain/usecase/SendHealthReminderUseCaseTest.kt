package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.feature.password_health.FakeHealthNotificationStateRepository
import de.davis.keygo.feature.password_health.FakeHealthSettingsRepository
import de.davis.keygo.feature.password_health.FakeNotifier
import de.davis.keygo.feature.password_health.data.LoginFingerprinterImpl
import de.davis.keygo.feature.password_health.domain.model.HealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthReminder
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.model.KeyGoNotification
import de.davis.keygo.feature.password_health.domain.model.VaultFingerprint
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.login
import de.davis.keygo.feature.password_health.domain.usecase.SendHealthReminderUseCase.Companion.MAX_REPEATS
import de.davis.keygo.feature.password_health.domain.usecase.SendHealthReminderUseCase.Companion.REMIND_AFTER
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SendHealthReminderUseCaseTest {

    private val loginRepository = FakeLoginRepository()
    private val fingerprinter = LoginFingerprinterImpl()
    private val settings = FakeHealthSettingsRepository(notificationsEnabled = true)
    private val state = FakeHealthNotificationStateRepository()
    private val notifier = FakeNotifier()

    private val useCase = SendHealthReminderUseCase(
        loginRepository = loginRepository,
        loginFingerprinter = fingerprinter,
        healthSettingsRepository = settings,
        healthNotificationStateRepository = state,
        notifier = notifier,
    )

    @Test
    fun theFirstSnapshotWithIssuesIsAnnounced() = runTest {
        snapshot(attention = 3)

        useCase(NOW)

        assertEquals(listOf(needsAttention(3)), notifier.sent)
        assertEquals(reminder(attention = 3, at = NOW), state.state.reminder)
    }

    @Test
    fun theSameIssuesAreNotRepeatedBeforeTheReminderIsDue() = runTest {
        snapshot(attention = 3, reminder = reminder(attention = 3, at = NOW))

        useCase(NOW + REMIND_AFTER - 1.days)

        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun theSameIssuesAreRemindedOnceTheReminderIsDue() = runTest {
        snapshot(attention = 3, reminder = reminder(attention = 3, at = NOW))
        val later = NOW + REMIND_AFTER

        useCase(later)

        assertEquals(listOf(needsAttention(3)), notifier.sent)
        assertEquals(reminder(attention = 3, at = later, repeats = 1), state.state.reminder)
    }

    @Test
    fun remindersStopAfterTheLimit() = runTest {
        snapshot(attention = 3, reminder = reminder(attention = 3, at = NOW, repeats = MAX_REPEATS))

        useCase(NOW + REMIND_AFTER * 3)

        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun moreIssuesAreAnnouncedRightAwayAndRestartTheReminders() = runTest {
        snapshot(attention = 4, reminder = reminder(attention = 3, at = NOW, repeats = MAX_REPEATS))
        val later = NOW + 1.days

        useCase(later)

        assertEquals(listOf(needsAttention(4)), notifier.sent)
        assertEquals(reminder(attention = 4, at = later), state.state.reminder)
    }

    @Test
    fun aNewBreachIsAnnouncedEvenWhenTheTotalStaysTheSame() = runTest {
        snapshot(attention = 3, breached = 1, reminder = reminder(attention = 3, at = NOW))

        useCase(NOW + 1.days)

        assertEquals(listOf(needsAttention(3)), notifier.sent)
    }

    @Test
    fun fewerIssuesAreRememberedQuietly() = runTest {
        snapshot(attention = 2, reminder = reminder(attention = 3, at = NOW, repeats = 1))

        useCase(NOW + 1.days)

        assertTrue(notifier.sent.isEmpty())
        assertEquals(reminder(attention = 2, at = NOW, repeats = 1), state.state.reminder)
    }

    @Test
    fun aFixedVaultWithdrawsTheNotification() = runTest {
        snapshot(attention = 0, reminder = reminder(attention = 3, at = NOW))

        useCase(NOW + 1.days)

        assertTrue(notifier.sent.isEmpty())
        assertEquals(listOf(KeyGoNotification.Kind.NeedsAttention), notifier.cancelled)
        assertNull(state.state.reminder)
    }

    @Test
    fun aSnapshotOfAnEditedVaultIsIgnored() = runTest {
        snapshot(attention = 3)
        loginRepository.seed(login(id(9)))

        useCase(NOW)

        assertTrue(notifier.sent.isEmpty())
        assertNull(state.state.reminder)
    }

    @Test
    fun withoutASnapshotTheNotificationIsWithdrawn() = runTest {
        state.state = HealthNotificationState(reminder = reminder(attention = 3, at = NOW))

        useCase(NOW + 1.days)

        assertTrue(notifier.sent.isEmpty())
        assertEquals(listOf(KeyGoNotification.Kind.NeedsAttention), notifier.cancelled)
        assertNull(state.state.reminder)
    }

    @Test
    fun nothingHappensWhenRemindersAreOff() = runTest {
        snapshot(attention = 3)
        settings.setNotificationEnabled(false)

        useCase(NOW)

        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun aBlockedNotificationIsNotCountedAsSent() = runTest {
        snapshot(attention = 3)
        notifier.canNotify = false

        useCase(NOW)

        assertTrue(notifier.sent.isEmpty())
        assertNull(state.state.reminder)
    }

    private suspend fun snapshot(
        attention: Int,
        breached: Int = 0,
        reminder: HealthReminder? = null,
    ) {
        loginRepository.seed(login(id(0)), login(id(1)))
        val vault = VaultFingerprint.of(
            loginRepository.observeLogins().first().mapNotNull { fingerprinter.fingerprint(it) },
        )
        state.state = HealthNotificationState(
            snapshot = HealthSnapshot(attention, breached, vault),
            reminder = reminder,
        )
    }

    private fun reminder(attention: Int, at: Instant, repeats: Int = 0) = HealthReminder(
        attentionCount = attention,
        breachedCount = 0,
        sentAt = at,
        repeats = repeats,
    )

    private fun needsAttention(count: Int): KeyGoNotification =
        KeyGoNotification.NeedsAttention(count)

    private companion object {
        val NOW = Instant.fromEpochSeconds(1_800_000_000)
    }
}

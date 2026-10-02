package de.davis.keygo.feature.password_health.data.repository

import de.davis.keygo.feature.password_health.data.local.model.ProtoHealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthNotificationState
import de.davis.keygo.feature.password_health.domain.model.HealthReminder
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.model.VaultFingerprint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HealthNotificationStateRepositoryImplTest {

    private val dataStore = FakeDataStore(ProtoHealthNotificationState.getDefaultInstance())
    private val repository = HealthNotificationStateRepositoryImpl(dataStore)

    private val snapshot = HealthSnapshot(3, 1, VaultFingerprint(byteArrayOf(1, 2, 3)))
    private val reminder = HealthReminder(3, 1, Instant.fromEpochMilliseconds(42), repeats = 1)

    @Test
    fun startsEmpty() = runTest {
        assertEquals(HealthNotificationState(), repository.get())
    }

    @Test
    fun snapshotAndReminderRoundTrip() = runTest {
        repository.setSnapshot(snapshot)
        repository.setReminder(reminder)

        assertEquals(HealthNotificationState(snapshot, reminder), repository.get())
    }

    @Test
    fun anEmptySnapshotIsStillASnapshot() = runTest {
        val empty = HealthSnapshot(0, 0, VaultFingerprint.of(emptyList()))

        repository.setSnapshot(empty)

        assertEquals(empty, repository.get().snapshot)
    }

    @Test
    fun nullRemovesOnlyThatPart() = runTest {
        repository.setSnapshot(snapshot)
        repository.setReminder(reminder)

        repository.setReminder(null)

        assertEquals(HealthNotificationState(snapshot = snapshot), repository.get())
    }

    @Test
    fun clearForgetsEverything() = runTest {
        repository.setSnapshot(snapshot)
        repository.setReminder(reminder)

        repository.clear()

        assertEquals(HealthNotificationState(), repository.get())
    }
}

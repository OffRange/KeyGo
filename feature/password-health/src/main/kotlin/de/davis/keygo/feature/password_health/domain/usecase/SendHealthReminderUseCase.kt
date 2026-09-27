package de.davis.keygo.feature.password_health.domain.usecase

import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.feature.password_health.domain.LoginFingerprinter
import de.davis.keygo.feature.password_health.domain.Notifier
import de.davis.keygo.feature.password_health.domain.model.HealthReminder
import de.davis.keygo.feature.password_health.domain.model.HealthSnapshot
import de.davis.keygo.feature.password_health.domain.model.KeyGoNotification
import de.davis.keygo.feature.password_health.domain.model.VaultFingerprint
import de.davis.keygo.feature.password_health.domain.repository.HealthNotificationStateRepository
import de.davis.keygo.feature.password_health.domain.repository.HealthSettingsRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@Single
class SendHealthReminderUseCase(
    private val loginRepository: LoginRepository,
    private val loginFingerprinter: LoginFingerprinter,
    private val healthSettingsRepository: HealthSettingsRepository,
    private val healthNotificationStateRepository: HealthNotificationStateRepository,
    private val notifier: Notifier,
) {

    suspend operator fun invoke(now: Instant = Clock.System.now()) {
        if (!healthSettingsRepository.getBreachCheckState().notificationsEnabled) return
        if (!notifier.canNotify()) return

        val state = healthNotificationStateRepository.get()
        val snapshot = state.snapshot
        if (snapshot == null || snapshot.attentionCount == 0) return withdraw()

        // Edited passwords would make the counts lie; the next unlock takes a new snapshot.
        if (snapshot.vault != currentVault()) return

        val last = state.reminder
        when {
            last == null || snapshot.isWorseThan(last) -> notify(snapshot, now, repeats = 0)
            last.isDue(now) -> notify(snapshot, now, repeats = last.repeats + 1)
            else -> lowerBaseline(last, snapshot)
        }
    }

    private suspend fun notify(snapshot: HealthSnapshot, now: Instant, repeats: Int) {
        notifier.sendNotification(KeyGoNotification.NeedsAttention(count = snapshot.attentionCount))
        healthNotificationStateRepository.setReminder(
            HealthReminder(
                attentionCount = snapshot.attentionCount,
                breachedCount = snapshot.breachedCount,
                sentAt = now,
                repeats = repeats,
            ),
        )
    }

    private suspend fun withdraw() {
        notifier.cancel(KeyGoNotification.Kind.NeedsAttention)
        healthNotificationStateRepository.setReminder(null)
    }

    // Issues were fixed since the last notification. Comparing against the old, higher counts
    // would hide a new issue that only brings the total back up, so compare against today's.
    // The reminder clock is kept: fixing things must not earn the user an extra notification.
    private suspend fun lowerBaseline(last: HealthReminder, snapshot: HealthSnapshot) {
        val lowered = last.copy(
            attentionCount = snapshot.attentionCount,
            breachedCount = snapshot.breachedCount,
        )
        if (lowered != last) healthNotificationStateRepository.setReminder(lowered)
    }

    private suspend fun currentVault(): VaultFingerprint = VaultFingerprint.of(
        loginRepository.observeLogins().first().mapNotNull { loginFingerprinter.fingerprint(it) },
    )

    private fun HealthSnapshot.isWorseThan(reminder: HealthReminder) =
        attentionCount > reminder.attentionCount || breachedCount > reminder.breachedCount

    private fun HealthReminder.isDue(now: Instant) =
        repeats < MAX_REPEATS && now - sentAt >= REMIND_AFTER

    companion object {
        val REMIND_AFTER = 30.days
        const val MAX_REPEATS = 2
    }
}

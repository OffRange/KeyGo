package de.davis.keygo.feature.password_health.data

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.davis.keygo.feature.password_health.domain.HealthCheckNotifierScheduler
import de.davis.keygo.feature.password_health.worker.HealthCheckNotifier
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

@Single
internal class HealthCheckNotifierSchedulerImpl(
    private val workManager: WorkManager,
) : HealthCheckNotifierScheduler {

    override fun scheduleHealthReminder() {
        // Delayed so turning reminders on does not immediately repeat the screen the user is on.
        val request = PeriodicWorkRequestBuilder<HealthCheckNotifier>(REPEAT_INTERVAL)
            .setInitialDelay(REPEAT_INTERVAL)
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = HealthCheckNotifier.UNIQUE_WORK_NAME,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            request = request,
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(HealthCheckNotifier.UNIQUE_WORK_NAME)
    }

    companion object {
        private val REPEAT_INTERVAL = 7.days.toJavaDuration()
    }
}

package de.davis.keygo.feature.password_health.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.davis.keygo.feature.password_health.domain.usecase.SendHealthReminderUseCase
import org.koin.android.annotation.KoinWorker

@KoinWorker
internal class HealthCheckNotifier(
    appContext: Context,
    params: WorkerParameters,
    private val sendHealthReminder: SendHealthReminderUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        sendHealthReminder()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "health-check-notifier"
    }
}

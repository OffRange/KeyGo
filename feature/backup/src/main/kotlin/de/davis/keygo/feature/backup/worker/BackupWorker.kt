package de.davis.keygo.feature.backup.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.android.annotation.KoinWorker

@KoinWorker
internal class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // TODO: backup
        Log.d("BackupWorker", "doing work")
        return Result.success()
    }
}
package de.davis.keygo.feature.backup.worker

import androidx.work.ListenableWorker
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import kotlin.test.Test
import kotlin.test.assertIs

class BackupWorkerResultTest {

    @Test
    fun `device locked retries`() {
        assertIs<ListenableWorker.Result.Retry>(
            resultFor(ExportProgress.Failed(ExportError.DeviceLocked), canRetry = true),
        )
    }

    @Test
    fun `session locked retries`() {
        assertIs<ListenableWorker.Result.Retry>(
            resultFor(ExportProgress.Failed(ExportError.SessionLocked), canRetry = true),
        )
    }

    @Test
    fun `device locked fails once attempts are spent`() {
        // Without this the job would retry forever, and its escrowed ARK would never be released.
        assertIs<ListenableWorker.Result.Failure>(
            resultFor(ExportProgress.Failed(ExportError.DeviceLocked), canRetry = false),
        )
    }

    @Test
    fun `session locked fails once attempts are spent`() {
        assertIs<ListenableWorker.Result.Failure>(
            resultFor(ExportProgress.Failed(ExportError.SessionLocked), canRetry = false),
        )
    }

    @Test
    fun `not provisioned fails`() {
        assertIs<ListenableWorker.Result.Failure>(
            resultFor(ExportProgress.Failed(ExportError.NotProvisioned), canRetry = true),
        )
    }

    @Test
    fun `succeeded returns success`() {
        assertIs<ListenableWorker.Result.Success>(
            resultFor(ExportProgress.Succeeded(1), canRetry = true),
        )
    }

    @Test
    fun `null terminal fails`() {
        assertIs<ListenableWorker.Result.Failure>(resultFor(null, canRetry = true))
    }
}

package de.davis.keygo.feature.backup.worker

import androidx.work.ListenableWorker
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import kotlin.test.Test
import kotlin.test.assertIs

class BackupWorkerResultTest {

    @Test
    fun `device locked retries`() {
        assertIs<ListenableWorker.Result.Retry>(resultFor(ExportProgress.Failed(ExportError.DeviceLocked)))
    }

    @Test
    fun `session locked retries`() {
        assertIs<ListenableWorker.Result.Retry>(resultFor(ExportProgress.Failed(ExportError.SessionLocked)))
    }

    @Test
    fun `not provisioned fails`() {
        assertIs<ListenableWorker.Result.Failure>(resultFor(ExportProgress.Failed(ExportError.NotProvisioned)))
    }

    @Test
    fun `succeeded returns success`() {
        assertIs<ListenableWorker.Result.Success>(resultFor(ExportProgress.Succeeded(1)))
    }

    @Test
    fun `null terminal fails`() {
        assertIs<ListenableWorker.Result.Failure>(resultFor(null))
    }
}

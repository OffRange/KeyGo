package de.davis.keygo.feature.backup.data.mapper

import androidx.work.WorkInfo
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DispatchedBackupMapperTest {

    @Test
    fun `maps work states to domain states`() {
        assertEquals(DispatchedBackup.State.Enqueued, toState(WorkInfo.State.ENQUEUED))
        assertEquals(DispatchedBackup.State.Enqueued, toState(WorkInfo.State.BLOCKED))
        assertEquals(DispatchedBackup.State.Running(), toState(WorkInfo.State.RUNNING))
        assertEquals(DispatchedBackup.State.Succeeded, toState(WorkInfo.State.SUCCEEDED))
        assertEquals(DispatchedBackup.State.Failed, toState(WorkInfo.State.FAILED))
        assertEquals(DispatchedBackup.State.Cancelled, toState(WorkInfo.State.CANCELLED))
    }

    @Test
    fun `running state carries the reported progress`() {
        assertEquals(
            DispatchedBackup.State.Running(ExportProgress.Running(2, 5)),
            toState(WorkInfo.State.RUNNING, ExportProgress.Running(2, 5)),
        )
    }

    @Test
    fun `recurring tag yields recurring kind`() {
        assertEquals(
            DispatchedBackup.Kind.Recurring,
            toKind(setOf(BackupWorker.TAG, BackupWorker.TAG_RECURRING)),
        )
    }

    @Test
    fun `without recurring tag yields one-time kind`() {
        assertEquals(
            DispatchedBackup.Kind.OneTime,
            toKind(setOf(BackupWorker.TAG, BackupWorker.TAG_ONE_TIME)),
        )
    }

    @Test
    fun `running phase with positive total yields running progress`() {
        assertEquals(
            ExportProgress.Running(2, 5),
            toProgress(phase = PROGRESS_PHASE_RUNNING, processed = 2, total = 5),
        )
    }

    @Test
    fun `running phase with non-positive total yields no progress`() {
        assertNull(toProgress(phase = PROGRESS_PHASE_RUNNING, processed = 0, total = 0))
        assertNull(toProgress(phase = PROGRESS_PHASE_RUNNING, processed = 1, total = -1))
    }

    @Test
    fun `writing phase yields writing progress`() {
        assertEquals(
            ExportProgress.Writing,
            toProgress(phase = PROGRESS_PHASE_WRITING, processed = 0, total = 0),
        )
    }

    @Test
    fun `unknown or absent phase yields no progress`() {
        assertNull(toProgress(phase = null, processed = 2, total = 5))
        assertNull(toProgress(phase = "unknown", processed = 2, total = 5))
    }

    @Test
    fun `progress data round-trips through work data`() {
        val running = ExportProgress.Running(2, 5).toProgressData()
        assertEquals(
            ExportProgress.Running(2, 5),
            toProgress(
                phase = running.getString(PROGRESS_KEY_PHASE),
                processed = running.getInt(PROGRESS_KEY_PROCESSED, 0),
                total = running.getInt(PROGRESS_KEY_TOTAL, 0),
            ),
        )

        val writing = ExportProgress.Writing.toProgressData()
        assertEquals(
            ExportProgress.Writing,
            toProgress(
                phase = writing.getString(PROGRESS_KEY_PHASE),
                processed = writing.getInt(PROGRESS_KEY_PROCESSED, 0),
                total = writing.getInt(PROGRESS_KEY_TOTAL, 0),
            ),
        )
    }
}

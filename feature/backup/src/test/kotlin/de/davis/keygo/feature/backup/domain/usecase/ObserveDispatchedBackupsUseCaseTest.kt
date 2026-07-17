package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.FakeDispatchedBackupRepository
import de.davis.keygo.feature.backup.data.FakeBackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.model.ExportProgress
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.worker.BackupWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveDispatchedBackupsUseCaseTest {

    private val repository = FakeDispatchedBackupRepository()
    private val jobRepository = FakeBackupJobRepository()
    private val destinationResolver = FakeBackupDestinationResolver()
    private val useCase =
        ObserveDispatchedBackupsUseCase(repository, jobRepository, destinationResolver)

    private fun status(
        id: String,
        kind: DispatchedBackup.Kind = DispatchedBackup.Kind.OneTime,
        state: DispatchedBackup.State = DispatchedBackup.State.Running,
        progress: ExportProgress.InFlight? = null,
    ) = BackupWorkStatus(id, kind, state, progress)

    @Test
    fun `enriches a one-time worker from its persisted job`() = runTest {
        jobRepository.jobs["work-1"] = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
        )
        destinationResolver.result = BackupDestination(
            provider = BackupDestination.Provider.ThirdParty("Drive"),
            displayPath = "Drive/Backups",
        )
        repository.statuses.value = listOf(status(id = "work-1"))

        val result = useCase().first().single()

        assertEquals("work-1", result.id)
        assertEquals(FileFormat.JSON, result.format)
        assertEquals(BackupDestination.Provider.ThirdParty("Drive"), result.destination?.provider)
    }

    @Test
    fun `recurring worker is enriched from the recurring job key`() = runTest {
        jobRepository.jobs[BackupWorker.RECURRING_WORK_ID] = BackupJob(
            uri = BackupDestinationUri("content://recurring.csv"),
            wrappedPassphrase = null,
            format = FileFormat.CSV,
        )
        repository.statuses.value = listOf(
            status(id = "any-runtime-id", kind = DispatchedBackup.Kind.Recurring),
        )

        val result = useCase().first().single()

        assertEquals(FileFormat.CSV, result.format)
        assertEquals(BackupDestinationUri("content://recurring.csv"), destinationResolver.lastUri)
    }

    @Test
    fun `missing job leaves format and destination null`() = runTest {
        repository.statuses.value = listOf(status(id = "orphan"))

        val result = useCase().first().single()

        assertNull(result.format)
        assertNull(result.destination)
    }

    @Test
    fun `progress passes through unchanged`() = runTest {
        repository.statuses.value = listOf(
            status(id = "w", progress = ExportProgress.Running(3, 7)),
        )

        val result = useCase().first().single()

        assertEquals(ExportProgress.Running(3, 7), result.progress)
    }

    @Test
    fun `writing progress passes through unchanged`() = runTest {
        repository.statuses.value = listOf(
            status(id = "w", progress = ExportProgress.Writing),
        )

        val result = useCase().first().single()

        assertEquals(ExportProgress.Writing, result.progress)
    }

    @Test
    fun `timestamp prefers finishedAt then createdAt`() = runTest {
        jobRepository.jobs["finished"] = BackupJob(
            uri = BackupDestinationUri("content://a.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            createdAt = 10L,
            finishedAt = 99L,
        )
        jobRepository.jobs["active"] = BackupJob(
            uri = BackupDestinationUri("content://b.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            createdAt = 42L,
        )
        repository.statuses.value = listOf(status(id = "finished"), status(id = "active"))

        val result = useCase().first()

        assertEquals(99L, result.first { it.id == "finished" }.timestamp)
        assertEquals(42L, result.first { it.id == "active" }.timestamp)
    }
}

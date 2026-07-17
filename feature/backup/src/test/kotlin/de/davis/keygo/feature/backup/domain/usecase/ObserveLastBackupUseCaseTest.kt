package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.data.FakeBackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.FileFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveLastBackupUseCaseTest {

    private val jobRepository = FakeBackupJobRepository()
    private val destinationResolver = FakeBackupDestinationResolver()
    private val useCase = ObserveLastBackupUseCase(jobRepository, destinationResolver)

    private fun job(
        uri: String,
        finishedAt: Long?,
        result: BackupResult?,
    ) = BackupJob(
        uri = BackupDestinationUri(uri),
        wrappedPassphrase = null,
        format = FileFormat.JSON,
        finishedAt = finishedAt,
        lastResult = result,
    )

    @Test
    fun `picks the newest successful finish across jobs`() = runTest {
        jobRepository.jobs["one-time"] = job("content://one.json", 100L, BackupResult.Success)
        jobRepository.jobs["recurring"] = job("content://rec.json", 300L, BackupResult.Success)
        jobRepository.jobs["failed"] = job("content://bad.json", 999L, BackupResult.Failure)
        destinationResolver.result = BackupDestination(
            provider = BackupDestination.Provider.ThirdParty("Drive"),
            displayPath = "Drive/Backups",
        )

        val last = useCase().first()

        assertEquals(300L, last?.finishedAt)
        assertEquals(BackupDestinationUri("content://rec.json"), destinationResolver.lastUri)
        assertEquals(destinationResolver.result, last?.destination)
    }

    @Test
    fun `null when no successful backup exists`() = runTest {
        jobRepository.jobs["failed"] = job("content://bad.json", 5L, BackupResult.Failure)
        jobRepository.jobs["never-run"] = job("content://idle.json", null, null)

        assertNull(useCase().first())
    }
}

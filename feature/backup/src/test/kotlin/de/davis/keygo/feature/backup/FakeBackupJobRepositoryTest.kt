package de.davis.keygo.feature.backup

import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupFailureReason
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.FileFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeBackupJobRepositoryTest {

    private val repository = FakeBackupJobRepository()

    private fun seed() {
        repository.jobs["w"] = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            createdAt = 1L,
        )
    }

    @Test
    fun `a failure records its reason`() = runTest {
        seed()

        repository.markFinished("w", BackupResult.Failure(BackupFailureReason.WriteFailed), 10L)

        val saved = repository.jobs.getValue("w")
        assertEquals(BackupResult.Failure(BackupFailureReason.WriteFailed), saved.lastResult)
    }

    @Test
    fun `a later success clears the previous reason`() = runTest {
        seed()
        repository.markFinished("w", BackupResult.Failure(BackupFailureReason.WriteFailed), 10L)

        repository.markFinished("w", BackupResult.Success, 20L)

        assertEquals(BackupResult.Success, repository.jobs.getValue("w").lastResult)
    }

    @Test
    fun `marking an absent record is a no-op`() = runTest {
        repository.markFinished(
            "missing",
            BackupResult.Failure(BackupFailureReason.CryptoFailed),
            10L
        )

        assertNull(repository.jobs["missing"])
    }
}

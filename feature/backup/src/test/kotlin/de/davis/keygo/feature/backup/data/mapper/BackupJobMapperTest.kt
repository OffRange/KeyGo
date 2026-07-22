package de.davis.keygo.feature.backup.data.mapper

import de.davis.keygo.feature.backup.data.local.model.protoBackupJob
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupFailureReason
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davis.keygo.feature.backup.domain.model.EncryptionMethod
import de.davis.keygo.feature.backup.domain.model.FileFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupJobMapperTest {

    @Test
    fun `round-trips created, finished and result`() {
        val job = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            createdAt = 100L,
            finishedAt = 200L,
            lastResult = BackupResult.Success,
        )

        val restored = job.toProto().toDomain()

        assertEquals(100L, restored.createdAt)
        assertEquals(200L, restored.finishedAt)
        assertEquals(BackupResult.Success, restored.lastResult)
        assertEquals(FileFormat.JSON, restored.format)
    }

    @Test
    fun `round-trips keepCount`() {
        val job = BackupJob(
            uri = BackupDestinationUri("content://tree"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            keepCount = 5,
        )

        assertEquals(5, job.toProto().toDomain().keepCount)
    }

    @Test
    fun `absent finished fields decode to null`() {
        val proto = protoBackupJob {
            uri = "content://out.csv"
            format = FileFormat.CSV.name
            createdAt = 5L
        }

        val restored = proto.toDomain()

        assertEquals(5L, restored.createdAt)
        assertNull(restored.finishedAt)
        assertNull(restored.lastResult)
        assertNull(restored.keepCount)
    }

    @Test
    fun `unparseable result decodes to null`() {
        val proto = protoBackupJob {
            uri = "content://out.json"
            format = FileFormat.JSON.name
            lastResult = "garbage"
        }

        assertNull(proto.toDomain().lastResult)
    }

    @Test
    fun `round-trips encryption method and csv preset`() {
        val job = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            encryption = EncryptionMethod.Ark,
        )

        assertEquals(EncryptionMethod.Ark, job.toProto().toDomain().encryption)

        val csvJob = BackupJob(
            uri = BackupDestinationUri("content://out.csv"),
            wrappedPassphrase = null,
            format = FileFormat.CSV,
            csvPreset = CsvPreset.KeyGo,
        )

        assertEquals(CsvPreset.KeyGo, csvJob.toProto().toDomain().csvPreset)
    }

    @Test
    fun `json job without encryption field defaults to Passphrase`() {
        val proto = protoBackupJob {
            uri = "content://out.json"
            format = FileFormat.JSON.name
        }

        val restored = proto.toDomain()

        assertEquals(EncryptionMethod.Passphrase, restored.encryption)
        assertNull(restored.csvPreset)
    }

    @Test
    fun `csv job without preset field defaults to Browser`() {
        val proto = protoBackupJob {
            uri = "content://out.csv"
            format = FileFormat.CSV.name
        }

        val restored = proto.toDomain()

        assertEquals(CsvPreset.Browser, restored.csvPreset)
        assertNull(restored.encryption)
    }

    @Test
    fun `unparseable encryption and preset fall back to defaults`() {
        val proto = protoBackupJob {
            uri = "content://out.json"
            format = FileFormat.JSON.name
            encryption = "garbage"
        }

        assertEquals(EncryptionMethod.Passphrase, proto.toDomain().encryption)

        val csvProto = protoBackupJob {
            uri = "content://out.csv"
            format = FileFormat.CSV.name
            csvPreset = "garbage"
        }

        assertEquals(CsvPreset.Browser, csvProto.toDomain().csvPreset)
    }

    @Test
    fun `cancelled round-trips through the proto`() {
        val job = BackupJob(
            uri = BackupDestinationUri("content://folder"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            cancelled = true,
        )

        assertTrue(job.toProto().toDomain().cancelled)
    }

    @Test
    fun `a job without the cancelled field is not cancelled`() {
        val job = BackupJob(
            uri = BackupDestinationUri("content://folder"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
        )

        assertFalse(job.toProto().toDomain().cancelled)
    }

    @Test
    fun `round-trips a failure reason`() {
        val job = BackupJob(
            uri = BackupDestinationUri("content://out.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            finishedAt = 200L,
            lastResult = BackupResult.Failure(BackupFailureReason.WriteFailed),
        )

        val restored = job.toProto().toDomain()

        assertEquals(BackupResult.Failure(BackupFailureReason.WriteFailed), restored.lastResult)
    }

    @Test
    fun `a failure with no persisted reason decodes to a null reason`() {
        val proto = protoBackupJob {
            uri = "content://out.json"
            format = FileFormat.JSON.name
            createdAt = 5L
            lastResult = "Failure"
        }

        assertEquals(BackupResult.Failure(null), proto.toDomain().lastResult)
    }

    @Test
    fun `an unrecognised failure reason still decodes to a failure`() {
        val proto = protoBackupJob {
            uri = "content://out.json"
            format = FileFormat.JSON.name
            createdAt = 5L
            lastResult = "Failure"
            lastError = "ReasonFromANewerBuild"
        }

        assertEquals(BackupResult.Failure(null), proto.toDomain().lastResult)
    }
}

package de.davis.keygo.feature.backup.domain.model

import de.davisalessandro.keygo.rust.BackupException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExportErrorTest {

    @Test
    fun `retryable errors have no persistable reason`() {
        assertNull(ExportError.SessionLocked.failureReason)
        assertNull(ExportError.DeviceLocked.failureReason)
    }

    @Test
    fun `terminal errors map to their reason`() {
        assertEquals(BackupFailureReason.NothingToExport, ExportError.NothingToExport.failureReason)
        assertEquals(BackupFailureReason.CryptoFailed, ExportError.CryptoFailed.failureReason)
        assertEquals(BackupFailureReason.WriteFailed, ExportError.WriteFailed.failureReason)
        assertEquals(BackupFailureReason.NotProvisioned, ExportError.NotProvisioned.failureReason)
    }

    @Test
    fun `a crypto serialization failure names the crypto sub-case`() {
        val error = ExportError.SerializationFailed(BackupException.Crypto("aead failure"))

        assertEquals(BackupFailureReason.CryptoSerializationFailed, error.failureReason)
    }

    @Test
    fun `a non-crypto serialization failure folds into the generic reason`() {
        val json = ExportError.SerializationFailed(BackupException.Json("field 'items' malformed"))
        val csv = ExportError.SerializationFailed(BackupException.Csv("row 3 malformed"))
        val empty = ExportError.SerializationFailed(BackupException.EmptyCsv())
        val header = ExportError.SerializationFailed(BackupException.MalformedHeader())

        assertEquals(BackupFailureReason.SerializationFailed, json.failureReason)
        assertEquals(BackupFailureReason.SerializationFailed, csv.failureReason)
        assertEquals(BackupFailureReason.SerializationFailed, empty.failureReason)
        assertEquals(BackupFailureReason.SerializationFailed, header.failureReason)
    }
}

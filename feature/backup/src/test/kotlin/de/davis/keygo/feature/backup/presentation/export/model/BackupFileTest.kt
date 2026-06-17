package de.davis.keygo.feature.backup.presentation.export.model

import de.davis.keygo.feature.backup.domain.model.FileFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupFileTest {

    @Test
    fun `kdbx format maps to a kdbx file name`() {
        assertEquals("keygo-backup.kdbx", FileFormat.KDBX.backupFileName())
    }

    @Test
    fun `csv format maps to a csv file name`() {
        assertEquals("keygo-backup.csv", FileFormat.CSV.backupFileName())
    }

    @Test
    fun `null format falls back to the base name`() {
        assertEquals("keygo-backup", (null as FileFormat?).backupFileName())
    }
}

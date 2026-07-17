package de.davis.keygo.feature.backup

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeBackupFileStoreTest {

    private val folder = BackupDestinationUri("content://tree")

    @Test
    fun `writeNewDocument records the call and lists the new document`() = runTest {
        val store = FakeBackupFileStore()

        val result =
            store.writeNewDocument(folder, "keygo-backup-1.json", "application/json", "hello")

        assertIs<Result.Success<Unit, Throwable>>(result)
        assertEquals("hello", store.writtenText)
        assertEquals("keygo-backup-1.json", store.writtenFileName)
        assertEquals(listOf("keygo-backup-1.json"), store.backups.map { it.name })
    }

    @Test
    fun `listBackups filters by base name`() = runTest {
        val store = FakeBackupFileStore()
        store.writeNewDocument(folder, "keygo-backup-1.json", "application/json", "a")
        store.writeNewDocument(folder, "unrelated.txt", "text/plain", "b")

        val listed =
            assertIs<Result.Success<List<*>, Throwable>>(store.listBackups(folder, "keygo-backup"))

        assertEquals(
            listOf("keygo-backup-1.json"),
            store.backups.filter { it.name.startsWith("keygo-backup") }.map { it.name })
        assertEquals(1, listed.success.size)
    }

    @Test
    fun `delete removes the document`() = runTest {
        val store = FakeBackupFileStore()
        store.writeNewDocument(folder, "keygo-backup-1.json", "application/json", "a")
        val entry = store.backups.single()

        store.delete(entry.uri)

        assertEquals(emptyList(), store.backups)
        assertEquals(listOf(entry.uri), store.deleted)
    }

    @Test
    fun `read surfaces the configured error`() = runTest {
        val store = FakeBackupFileStore().apply { readError = RuntimeException("boom") }
        assertIs<Result.Failure<Nothing, Throwable>>(store.read(BackupDestinationUri("content://doc")))
    }
}

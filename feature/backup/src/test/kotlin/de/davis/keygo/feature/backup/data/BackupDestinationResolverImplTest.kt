package de.davis.keygo.feature.backup.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class BackupDestinationResolverImplTest {

    private val context = RuntimeEnvironment.getApplication()
    private val resolver = BackupDestinationResolverImpl(context)

    @Test
    fun `a revoked folder grant degrades to the document id instead of crashing`() = runTest {
        ShadowContentResolver.registerProviderInternal(AUTHORITY, DeniedProvider())

        val destination = resolver.resolve(BackupDestinationUri(TREE_URI))

        assertEquals("folder:Backups", destination.displayPath)
        assertEquals(null, destination.fileName)
    }

    @Test
    fun `a folder the provider still answers for shows its display name`() = runTest {
        ShadowContentResolver.registerProviderInternal(AUTHORITY, NamingProvider())

        val destination = resolver.resolve(BackupDestinationUri(TREE_URI))

        assertEquals("Backups", destination.displayPath)
    }

    @Test
    fun `a cached name survives a provider that refuses to answer`() = runTest {
        ShadowContentResolver.registerProviderInternal(AUTHORITY, DeniedProvider())

        val destination = resolver.resolve(BackupDestinationUri(TREE_URI), cachedName = "Backups")

        assertEquals("Backups", destination.displayPath)
    }

    @Test
    fun `a cached name is trusted over asking the provider again`() = runTest {
        ShadowContentResolver.registerProviderInternal(AUTHORITY, NamingProvider())

        val destination = resolver.resolve(BackupDestinationUri(TREE_URI), cachedName = "Renamed")

        assertEquals("Renamed", destination.displayPath)
    }

    /** Stands in for a provider that no longer honours a persisted grant. */
    private class DeniedProvider : StubProvider() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = throw SecurityException("Permission Denial: reading $uri")
    }

    private class NamingProvider : StubProvider() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            .apply { addRow(arrayOf("Backups")) }
    }

    private abstract class StubProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    companion object {
        private const val AUTHORITY = "com.example.docs"
        private const val TREE_URI = "content://$AUTHORITY/tree/folder%3ABackups"
    }
}

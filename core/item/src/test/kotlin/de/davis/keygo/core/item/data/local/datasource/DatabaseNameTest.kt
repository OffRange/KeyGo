package de.davis.keygo.core.item.data.local.datasource

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v1 shipped its Room database as `secure_element_database` under the same applicationId. If
 * `ItemDatabase` ever takes that name back, Room reads version 3 out of the inherited v1 file,
 * compares it against version 1 and throws, and `:migration:legacy-data` loses the file it reads
 * from. Guarding the literal is cheaper than rediscovering that on a device.
 */
class DatabaseNameTest {

    private val source = File("src/main/kotlin/de/davis/keygo/core/item/data/local/datasource/ItemDatabase.kt")
        .readText()

    @Test
    fun `does not reuse the v1 database file name`() {
        assertFalse(
            source.contains("secure_element_database"),
            "ItemDatabase must not use the v1 file name; :migration:legacy-data reads that file.",
        )
    }

    @Test
    fun `uses the expected database file name`() {
        assertTrue(
            source.contains("\"keygo_database\""),
            "ItemDatabase file name changed; update :migration:legacy-data and this guard together.",
        )
    }
}

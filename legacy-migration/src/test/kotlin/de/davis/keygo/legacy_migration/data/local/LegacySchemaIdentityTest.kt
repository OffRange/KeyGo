package de.davis.keygo.legacy_migration.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the ported entities against drifting from v1's frozen schema.
 *
 * The identity hash is the whole assertion, because it is the whole of what Room itself checks:
 * Room compares `room_master_table.identity_hash` against the hash derived from the declared
 * entities and refuses to open the file on a mismatch. The hash is computed over sorted fields, so
 * it pins column names, affinities, nullability, defaults, the primary key, indices and foreign
 * keys. If it matches v1's, the ported entities describe v1's database, and every real v1 file on
 * every device still opens.
 *
 * What the hash does not pin is the order the columns are declared in, and nothing else needs to.
 * Room validates an opened file by matching columns by name, so declaration order is inert at
 * runtime and there is nothing there for a test to protect.
 *
 * These hashes are v1's, read from `origin/v1:app/schemas/...KeyGoDatabase/`. They are frozen: v1
 * will never ship another version. A failure here means the port drifted, not that the expectation
 * is stale. Versions 1 and 2 are not generated from anything, they *are* v1's own files, copied
 * verbatim into the schema directory so Room can generate the 1-to-2 migration from them; asserting
 * their hashes is what catches an edit to those copies.
 */
class LegacySchemaIdentityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun identityHashOf(version: Int): String =
        json.parseToJsonElement(LEGACY_SCHEMA_DIR.resolve("$version.json").toFile().readText())
            .jsonObject
            .getValue("database")
            .jsonObject
            .getValue("identityHash")
            .jsonPrimitive
            .content

    @Test
    fun `version 3 identity hash matches v1`() {
        assertEquals(
            "0a97d13a94575bc2ed2ab009853b0086",
            identityHashOf(3),
            "Ported entities no longer generate v1's version 3 schema.",
        )
    }

    @Test
    fun `copied version 1 and 2 schemas are v1's own`() {
        assertEquals("6fb485e6e64b1e9cc5ceeb0440c58e23", identityHashOf(1))
        assertEquals("d5dd1a0120d5842be17e8ea9a19ee039", identityHashOf(2))
    }
}

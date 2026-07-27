package de.davis.keygo.migration.legacy_data.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the ported entities against drifting from v1's frozen schema, with two assertions that
 * cover different things. Neither is redundant, and deleting either one leaves a real gap.
 *
 * The identity hash is what Room itself checks: it compares `room_master_table.identity_hash`
 * against the hash derived from the declared entities and refuses to open the file on a mismatch.
 * That hash is computed over sorted fields, so it pins column names, affinities, nullability,
 * defaults, the primary key, indices and foreign keys, but it is blind to the order the columns are
 * declared in. A drift on anything it covers stops every real v1 database on every device opening.
 *
 * The statement-for-statement DDL comparison is what pins column order. Order is runtime-inert,
 * since Room validates an opened file by matching columns by name, so a drift here breaks nothing
 * at runtime. It is asserted anyway so the schema we ship stays byte-identical to the one v1
 * shipped, which is what makes the exported JSON trustworthy as a record of v1 rather than a
 * near-miss.
 *
 * These hashes are v1's, read from `origin/v1:app/schemas/...KeyGoDatabase/`. They are frozen: v1
 * will never ship another version. A failure here means the port drifted, not that the expectation
 * is stale.
 */
class LegacySchemaIdentityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val exportedDir = File(
        "schemas/de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase",
    )
    private val pristineDir = File("src/test/resources/legacy-schemas")

    private fun identityHashOf(file: File): String =
        json.parseToJsonElement(file.readText())
            .jsonObject
            .getValue("database")
            .jsonObject
            .getValue("identityHash")
            .jsonPrimitive
            .content

    private fun createSqlOf(file: File): List<String> =
        json.parseToJsonElement(file.readText())
            .jsonObject
            .getValue("database")
            .jsonObject
            .getValue("entities")
            .let { it as JsonArray }
            .map { entity ->
                val obj = entity as JsonObject
                obj.getValue("createSql").jsonPrimitive.content
                    .replace("\${TABLE_NAME}", obj.getValue("tableName").jsonPrimitive.content)
            }
            .sorted()

    @Test
    fun `version 3 identity hash matches v1`() {
        assertEquals(
            "0a97d13a94575bc2ed2ab009853b0086",
            identityHashOf(File(exportedDir, "3.json")),
            "Ported entities no longer generate v1's version 3 schema.",
        )
    }

    @Test
    fun `version 3 DDL matches v1 statement for statement`() {
        assertEquals(
            createSqlOf(File(pristineDir, "3.json")),
            createSqlOf(File(exportedDir, "3.json")),
            "Ported entity DDL drifted from v1. Column order counts.",
        )
    }

    @Test
    fun `copied version 1 and 2 schemas are v1's own`() {
        assertEquals("6fb485e6e64b1e9cc5ceeb0440c58e23", identityHashOf(File(exportedDir, "1.json")))
        assertEquals("d5dd1a0120d5842be17e8ea9a19ee039", identityHashOf(File(exportedDir, "2.json")))
    }
}

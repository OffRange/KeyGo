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
 * Room compares `room_master_table.identity_hash` against the hash derived from the declared
 * entities when it opens a file. If the ported entities drift from v1's schema by so much as a
 * column order, every real v1 database on every device stops opening.
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

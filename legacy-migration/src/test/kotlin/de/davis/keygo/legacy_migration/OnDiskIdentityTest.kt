package de.davis.keygo.legacy_migration

import com.google.protobuf.timestamp
import de.davis.keygo.legacy_migration.data.local.datasource.LEGACY_DATABASE_NAME
import de.davis.keygo.legacy_migration.data.local.model.protoMainPassword
import de.davis.keygo.legacy_migration.data.repository.LEGACY_KEY_ALIAS
import de.davis.keygo.legacy_migration.di.MAIN_PASSWORD_DATA_STORE_NAME
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The four names below are the identity of a shipped v1 install's data on disk. Renaming the module
 * or the Kotlin package around them is safe. Renaming them is not: it orphans the user's database,
 * their main password record or their item key, with no way back.
 *
 * These assertions look tautological on purpose. They are here so that changing one of the values
 * cannot be done quietly.
 */
class OnDiskIdentityTest {

    @Test
    fun `legacy room database keeps v1's file name`() {
        assertEquals("secure_element_database", LEGACY_DATABASE_NAME)
    }

    @Test
    fun `main password datastore keeps v1's file name`() {
        assertEquals("main-password.db", MAIN_PASSWORD_DATA_STORE_NAME)
    }

    @Test
    fun `legacy keystore alias keeps v1's value`() {
        assertEquals("password_manager_skey", LEGACY_KEY_ALIAS)
    }

    /**
     * The proto's `java_package` moved with the module. That renames the generated Kotlin class and
     * nothing else, so an existing `main-password.db` still parses. This pins the wire format that
     * makes the claim true: field 1 is the hash, field 2 is the timestamp.
     */
    @Test
    fun `main password proto keeps v1's field numbers`() {
        val encoded = protoMainPassword {
            hash = "ab"
            createdAt = timestamp { seconds = 1 }
        }.toByteArray()

        assertContentEquals(
            byteArrayOf(
                0x0A, 0x02, 0x61, 0x62, // field 1, length-delimited, "ab"
                0x12, 0x02, 0x08, 0x01, // field 2, length-delimited, Timestamp { seconds = 1 }
            ),
            encoded,
        )
    }
}

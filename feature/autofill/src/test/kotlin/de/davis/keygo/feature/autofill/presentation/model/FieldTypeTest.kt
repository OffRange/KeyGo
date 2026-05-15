package de.davis.keygo.feature.autofill.presentation.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldTypeTest {

    @Test
    fun `Username group is Credentials`() {
        assertEquals(FieldType.Credentials::class, FieldType.Credentials.Username.group)
    }

    @Test
    fun `EMail group is Credentials`() {
        assertEquals(FieldType.Credentials::class, FieldType.Credentials.EMail.group)
    }

    @Test
    fun `Phone group is Credentials`() {
        assertEquals(FieldType.Credentials::class, FieldType.Credentials.Phone.group)
    }

    @Test
    fun `Password group is Credentials`() {
        assertEquals(FieldType.Credentials::class, FieldType.Credentials.Password.group)
    }

    @Test
    fun `TOTP group is TOTP`() {
        assertEquals(FieldType.TOTP::class, FieldType.TOTP.group)
    }

    @Test
    fun `Undefined group is Undefined`() {
        assertEquals(FieldType.Undefined::class, FieldType.Undefined.group)
    }

    @Test
    fun `TOTP is excluded from save info`() {
        assertFalse(FieldType.TOTP.includeInSaveInfo)
    }

    @Test
    fun `credentials are included in save info`() {
        assertTrue(FieldType.Credentials.Username.includeInSaveInfo)
        assertTrue(FieldType.Credentials.EMail.includeInSaveInfo)
        assertTrue(FieldType.Credentials.Phone.includeInSaveInfo)
        assertTrue(FieldType.Credentials.Password.includeInSaveInfo)
    }

    @Test
    fun `Undefined is included in save info`() {
        assertTrue(FieldType.Undefined.includeInSaveInfo)
    }
}

package de.davis.keygo.feature.autofill.presentation.mapper

import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.FormType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FieldTypeToFormTypeMapperTest {

    @Test
    fun `Username maps to Credentials form type`() {
        assertEquals(FormType.Credentials, FieldType.Credentials.Username.toFormType())
    }

    @Test
    fun `EMail maps to Credentials form type`() {
        assertEquals(FormType.Credentials, FieldType.Credentials.EMail.toFormType())
    }

    @Test
    fun `Phone maps to Credentials form type`() {
        assertEquals(FormType.Credentials, FieldType.Credentials.Phone.toFormType())
    }

    @Test
    fun `Password maps to Credentials form type`() {
        assertEquals(FormType.Credentials, FieldType.Credentials.Password.toFormType())
    }

    @Test
    fun `TOTP maps to TOTP form type`() {
        assertEquals(FormType.TOTP, FieldType.TOTP.toFormType())
    }

    @Test
    fun `Undefined throws`() {
        assertFailsWith<IllegalArgumentException> {
            FieldType.Undefined.toFormType()
        }
    }
}

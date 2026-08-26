package de.davis.keygo.feature.autofill.presentation.dataset

import android.os.Bundle
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import de.davis.keygo.core.feature.autofill.autofillId
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormField
import de.davis.keygo.feature.autofill.presentation.model.FormType
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class SaveInfoAppenderTest {

    private fun credentialsForm(
        fields: List<FormField>,
        url: String? = "https://example.com",
    ) = Form(
        type = FormType.Credentials,
        fields = fields,
        url = url,
        isBrowser = false,
        appPackageName = "com.example",
        isSuspicious = false,
    )

    private fun totpForm(
        fields: List<FormField>,
        url: String? = "https://example.com",
    ) = Form(
        type = FormType.TOTP,
        fields = fields,
        url = url,
        isBrowser = false,
        appPackageName = "com.example",
        isSuspicious = false,
    )

    /**
     * A form of a type other than [FormType.Credentials] that still carries savable fields. There
     * is no such form yet, [FormType.TOTP] only ever holds fields we cannot save, so this stands in
     * for the form types still to come (a credit card, for example).
     */
    private fun otherTypeForm(
        fields: List<FormField>,
        url: String? = "https://example.com",
    ) = totpForm(fields = fields, url = url)

    private fun field(
        type: FieldType,
        viewId: Int = 1,
        url: String? = "https://example.com",
    ) = FormField(
        autofillId = autofillId(viewId),
        type = type,
        focused = false,
        url = url,
    )

    @Test
    fun `password field adds SAVE_DATA_TYPE_PASSWORD`() {
        val form = credentialsForm(fields = listOf(field(FieldType.Credentials.Password)))
        val (_, saveType) = Bundle().updateState(requestId = 1, form = form)
        assertTrue((saveType and SaveInfo.SAVE_DATA_TYPE_PASSWORD) != 0)
    }

    @Test
    fun `username field adds SAVE_DATA_TYPE_USERNAME`() {
        val form = credentialsForm(fields = listOf(field(FieldType.Credentials.Username)))
        val (_, saveType) = Bundle().updateState(requestId = 1, form = form)
        assertTrue((saveType and SaveInfo.SAVE_DATA_TYPE_USERNAME) != 0)
    }

    @Test
    fun `email field adds SAVE_DATA_TYPE_EMAIL_ADDRESS`() {
        val form = credentialsForm(fields = listOf(field(FieldType.Credentials.EMail)))
        val (_, saveType) = Bundle().updateState(requestId = 1, form = form)
        assertTrue((saveType and SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS) != 0)
    }

    @Test
    fun `phone field maps to SAVE_DATA_TYPE_GENERIC`() {
        val form = credentialsForm(fields = listOf(field(FieldType.Credentials.Phone)))
        val (_, saveType) = Bundle().updateState(requestId = 1, form = form)
        assertEquals(SaveInfo.SAVE_DATA_TYPE_GENERIC, saveType)
    }

    @Test
    fun `totp field maps to SAVE_DATA_TYPE_GENERIC`() {
        val form = totpForm(fields = listOf(field(FieldType.TOTP)))
        val (_, saveType) = Bundle().updateState(requestId = 1, form = form)
        assertEquals(SaveInfo.SAVE_DATA_TYPE_GENERIC, saveType)
    }

    @Test
    fun `a form without savable fields stores no form to build a save info from`() {
        val form = totpForm(fields = listOf(field(FieldType.TOTP)))
        val (bundle, _) = Bundle().updateState(requestId = 1, form = form)
        assertNull(bundle.getForm())
    }

    @Test
    fun `a form without savable fields keeps the collected fields`() {
        val credentials = credentialsForm(
            fields = listOf(field(FieldType.Credentials.Password, viewId = 1)),
        )
        val (firstBundle, firstSaveType) = Bundle().updateState(requestId = 1, form = credentials)

        val otp = totpForm(fields = listOf(field(FieldType.TOTP, viewId = 2)))
        val (secondBundle, secondSaveType) = firstBundle.updateState(requestId = 2, form = otp)

        val storedForm = secondBundle.getForm()
        assertNotNull(storedForm)
        assertEquals(listOf(FieldType.Credentials.Password), storedForm.fields.map { it.type })
        assertEquals(firstSaveType, secondSaveType)
    }

    @Test
    fun `undefined field maps to SAVE_DATA_TYPE_GENERIC`() {
        val form = credentialsForm(fields = listOf(field(FieldType.Undefined)))
        val (_, saveType) = Bundle().updateState(requestId = 1, form = form)
        assertEquals(SaveInfo.SAVE_DATA_TYPE_GENERIC, saveType)
    }

    @Test
    fun `multiple field types OR their save types together`() {
        val form = credentialsForm(
            fields = listOf(
                field(FieldType.Credentials.Username, viewId = 1),
                field(FieldType.Credentials.Password, viewId = 2),
            ),
        )
        val (_, saveType) = Bundle().updateState(requestId = 1, form = form)
        assertTrue((saveType and SaveInfo.SAVE_DATA_TYPE_USERNAME) != 0)
        assertTrue((saveType and SaveInfo.SAVE_DATA_TYPE_PASSWORD) != 0)
    }

    @Test
    fun `parcelable round-trip preserves form`() {
        val form = credentialsForm(fields = listOf(field(FieldType.Credentials.Password)))
        val (bundle, _) = Bundle().updateState(requestId = 1, form = form)
        val recovered = bundle.getForm()
        assertNotNull(recovered)
        assertEquals(FieldType.Credentials.Password, recovered.fields.first().type)
    }

    @Test
    fun `a savable form of another type starts a new save context`() {
        val firstForm = credentialsForm(fields = listOf(field(FieldType.Credentials.Password)))
        val (firstBundle, firstSaveType) = Bundle().updateState(requestId = 1, form = firstForm)
        assertTrue((firstSaveType and SaveInfo.SAVE_DATA_TYPE_PASSWORD) != 0)

        val secondForm = otherTypeForm(
            fields = listOf(field(FieldType.Credentials.Username, viewId = 2)),
        )
        val (secondBundle, secondSaveType) = firstBundle.updateState(
            requestId = 2,
            form = secondForm,
        )

        val storedForm = secondBundle.getForm()
        assertNotNull(storedForm)
        assertEquals(listOf(FieldType.Credentials.Username), storedForm.fields.map { it.type })
        assertEquals(SaveInfo.SAVE_DATA_TYPE_USERNAME, secondSaveType)
    }

    @Test
    fun `merge filters out fields with a different url`() {
        val formUrl = "https://example.com"
        val firstForm = credentialsForm(
            fields = listOf(field(FieldType.Credentials.Username, viewId = 1, url = formUrl)),
            url = formUrl,
        )
        val (firstBundle, _) = Bundle().updateState(requestId = 1, form = firstForm)

        val secondForm = credentialsForm(
            fields = listOf(
                field(
                    FieldType.Credentials.Password,
                    viewId = 2,
                    url = "https://other.com"
                )
            ),
            url = formUrl,
        )
        val (secondBundle, _) = firstBundle.updateState(requestId = 2, form = secondForm)

        val mergedForm = secondBundle.getForm()
        assertNotNull(mergedForm)
        assertFalse(mergedForm.fields.any { it.type == FieldType.Credentials.Password })
    }

    @Test
    fun `merge drops fields that are excluded from save info`() {
        val formUrl = "https://example.com"
        val firstForm = credentialsForm(
            fields = listOf(
                field(FieldType.TOTP, viewId = 1, url = formUrl),
            ),
            url = formUrl,
        )
        val (firstBundle, _) = Bundle().updateState(requestId = 1, form = firstForm)

        val secondForm = credentialsForm(
            fields = listOf(field(FieldType.Credentials.Password, viewId = 2, url = formUrl)),
            url = formUrl,
        )
        val (secondBundle, _) = firstBundle.updateState(requestId = 2, form = secondForm)

        val mergedForm = secondBundle.getForm()
        assertNotNull(mergedForm)
        assertFalse(mergedForm.fields.any { it.type == FieldType.TOTP })
    }

    @Test
    fun `merge deduplicates fields by autofill id`() {
        val formUrl = "https://example.com"
        val sharedId = autofillId(viewId = 42)
        val firstForm = Form(
            type = FormType.Credentials,
            fields = listOf(
                FormField(
                    autofillId = sharedId,
                    type = FieldType.Credentials.Username,
                    focused = false,
                    url = formUrl,
                ),
            ),
            url = formUrl,
            isBrowser = false,
            appPackageName = "com.example",
            isSuspicious = false,
        )
        val (firstBundle, _) = Bundle().updateState(requestId = 1, form = firstForm)

        val secondForm = Form(
            type = FormType.Credentials,
            fields = listOf(
                FormField(
                    autofillId = sharedId,
                    type = FieldType.Credentials.Username,
                    focused = false,
                    url = formUrl,
                ),
            ),
            url = formUrl,
            isBrowser = false,
            appPackageName = "com.example",
            isSuspicious = false,
        )
        val (secondBundle, _) = firstBundle.updateState(requestId = 2, form = secondForm)

        val mergedForm = secondBundle.getForm()
        assertNotNull(mergedForm)
        assertEquals(1, mergedForm.fields.size)
    }

    @Test
    fun `no save info is applied for a form without savable fields`() {
        val form = totpForm(fields = listOf(field(FieldType.TOTP)))

        val applied = FillResponse.Builder().applySaveInfo(
            form = form,
            clientInfo = Bundle(),
            requestId = 1,
            inCompatibilityMode = false,
        )

        assertFalse(applied)
    }

    @Test
    fun `a form without savable fields keeps the save info of the fields collected before`() {
        val credentials = credentialsForm(
            fields = listOf(field(FieldType.Credentials.Password, viewId = 1)),
        )
        val (clientState, _) = Bundle().updateState(requestId = 1, form = credentials)

        val otp = totpForm(fields = listOf(field(FieldType.TOTP, viewId = 2)))
        val applied = FillResponse.Builder().applySaveInfo(
            form = otp,
            clientInfo = clientState,
            requestId = 2,
            inCompatibilityMode = false,
        )

        assertTrue(applied)
    }

    @Test
    fun `the password alone is required, the remaining fields are optional`() {
        val username = field(FieldType.Credentials.Username, viewId = 1)
        val password = field(FieldType.Credentials.Password, viewId = 2)
        val form = credentialsForm(fields = listOf(username, password))

        val saveIds = form.toSaveIds()

        assertContentEquals(listOf(password.autofillId), saveIds.required)
        assertContentEquals(listOf(username.autofillId), saveIds.optional)
    }

    @Test
    fun `without a password every field is required`() {
        val username = field(FieldType.Credentials.Username, viewId = 1)
        val email = field(FieldType.Credentials.EMail, viewId = 2)
        val form = credentialsForm(fields = listOf(username, email))

        val saveIds = form.toSaveIds()

        assertContentEquals(listOf(username.autofillId, email.autofillId), saveIds.required)
        assertTrue(saveIds.optional.isEmpty())
    }
}

package de.davis.keygo.feature.autofill.presentation.dataset

import android.os.Bundle
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `second call with a different form type throws`() {
        val firstForm = credentialsForm(fields = listOf(field(FieldType.Credentials.Password)))
        val (firstBundle, _) = Bundle().updateState(requestId = 1, form = firstForm)

        val secondForm = totpForm(fields = listOf(field(FieldType.TOTP, viewId = 2)))
        assertFailsWith<IllegalArgumentException> {
            firstBundle.updateState(requestId = 2, form = secondForm)
        }
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
}

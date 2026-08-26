package de.davis.keygo.feature.autofill.presentation.sms

import android.app.PendingIntent
import android.content.Intent
import com.google.android.gms.auth.api.phone.SmsCodeRetriever
import com.google.android.gms.auth.api.phone.SmsRetrieverStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import de.davis.keygo.core.util.Result
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Covers [Intent.toSmsCodeResult], the pure mapping from a broadcast [Status] to a
 * [SmsCodeFailure]-carrying [Result]. The USER_PERMISSION_REQUIRED branch in particular is
 * behaviour the design doc inferred from the Play services API surface rather than from
 * documentation, so it is worth pinning down explicitly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class GmsSmsCodeRepositoryTest {

    private fun testPendingIntent(): PendingIntent = PendingIntent.getActivity(
        RuntimeEnvironment.getApplication(),
        0,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE,
    )

    @Test
    fun `success with a code returns Success`() {
        val intent = Intent().putExtra(SmsCodeRetriever.EXTRA_SMS_CODE, "123456")
        val status = Status(CommonStatusCodes.SUCCESS)

        val result = intent.toSmsCodeResult(status)

        assertIs<Result.Success<String, SmsCodeFailure>>(result)
        assertEquals("123456", result.success)
    }

    @Test
    fun `success with a missing code returns Unknown`() {
        val intent = Intent()
        val status = Status(CommonStatusCodes.SUCCESS)

        val result = intent.toSmsCodeResult(status)

        assertIs<Result.Failure<String, SmsCodeFailure>>(result)
        assertIs<SmsCodeFailure.Unknown>(result.error)
    }

    @Test
    fun `success with a blank code returns Unknown`() {
        val intent = Intent().putExtra(SmsCodeRetriever.EXTRA_SMS_CODE, "   ")
        val status = Status(CommonStatusCodes.SUCCESS)

        val result = intent.toSmsCodeResult(status)

        assertIs<Result.Failure<String, SmsCodeFailure>>(result)
        assertIs<SmsCodeFailure.Unknown>(result.error)
    }

    @Test
    fun `timeout returns Timeout`() {
        val intent = Intent()
        val status = Status(CommonStatusCodes.TIMEOUT)

        val result = intent.toSmsCodeResult(status)

        assertIs<Result.Failure<String, SmsCodeFailure>>(result)
        assertEquals(SmsCodeFailure.Timeout, result.error)
    }

    @Test
    fun `permission required with a resolution returns ConsentRequired`() {
        val pendingIntent = testPendingIntent()
        val intent = Intent()
        val status = Status(
            SmsRetrieverStatusCodes.USER_PERMISSION_REQUIRED,
            "permission required",
            pendingIntent,
        )

        val result = intent.toSmsCodeResult(status)

        assertIs<Result.Failure<String, SmsCodeFailure>>(result)
        val failure = assertIs<SmsCodeFailure.ConsentRequired>(result.error)
        assertEquals(pendingIntent.intentSender, failure.intentSender)
    }

    @Test
    fun `permission required without a resolution returns Unavailable`() {
        val intent = Intent()
        val status = Status(SmsRetrieverStatusCodes.USER_PERMISSION_REQUIRED)

        val result = intent.toSmsCodeResult(status)

        assertIs<Result.Failure<String, SmsCodeFailure>>(result)
        assertEquals(SmsCodeFailure.Unavailable, result.error)
    }

    @Test
    fun `unrecognised status code returns Unknown`() {
        val intent = Intent()
        val status = Status(999_999)

        val result = intent.toSmsCodeResult(status)

        assertIs<Result.Failure<String, SmsCodeFailure>>(result)
        assertIs<SmsCodeFailure.Unknown>(result.error)
    }

    @Test
    fun `null status returns Unknown`() {
        val intent = Intent()

        val result = intent.toSmsCodeResult(null)

        assertIs<Result.Failure<String, SmsCodeFailure>>(result)
        assertIs<SmsCodeFailure.Unknown>(result.error)
    }
}

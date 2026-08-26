package de.davis.keygo.feature.autofill.presentation.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.android.gms.auth.api.phone.SmsCodeAutofillClient
import com.google.android.gms.auth.api.phone.SmsCodeRetriever
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.auth.api.phone.SmsRetrieverStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds

@Single
internal class GmsSmsCodeRepository(
    private val context: Context,
) : SmsCodeRepository {

    private val client by lazy {
        SmsCodeRetriever.getAutofillClient(context)
    }

    override suspend fun canOfferSuggestion(targetPackage: String): Boolean = try {
        withTimeoutOrNull(PLAY_SERVICES_TIMEOUT) {
            if (client.hasOngoingSmsRequest(targetPackage).await()) return@withTimeoutOrNull false

            when (client.checkPermissionState().await()) {
                SmsCodeAutofillClient.PermissionState.GRANTED,
                SmsCodeAutofillClient.PermissionState.NONE -> true

                else -> false
            }
        } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "SMS code autofill unavailable", e)
        false
    }

    override suspend fun retrieveSmsCode(): Result<String, SmsCodeFailure> {
        val code = CompletableDeferred<Result<String, SmsCodeFailure>>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION) return
                val status = IntentCompat.getParcelableExtra(
                    intent, SmsRetriever.EXTRA_STATUS, Status::class.java,
                )
                code.complete(intent.toSmsCodeResult(status))
            }
        }

        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION),
                SmsRetriever.SEND_PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not register the sms code receiver", e)
            return Result.Failure(SmsCodeFailure.Unknown(e))
        }

        return try {
            // Start only after the receiver is live, otherwise a code that is
            // already sitting in the inbox can be delivered before you listen.
            client.startSmsCodeRetriever().await()
            code.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e.toSmsCodeFailure())
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun Exception.toSmsCodeFailure(): SmsCodeFailure = when (this) {
        is ResolvableApiException -> {
            // ResolvableApiException.getResolution() is annotated @NonNull, but its
            // implementation just delegates to Status.getResolution(), which the
            // library itself annotates @Nullable and backs with a plain field. The
            // annotation is not honored by the implementation, so the safe call stays.
            @Suppress("UNNECESSARY_SAFE_CALL")
            val intentSender = resolution?.intentSender
            if (intentSender == null) SmsCodeFailure.Unavailable
            else SmsCodeFailure.ConsentRequired(intentSender)
        }

        else -> SmsCodeFailure.Unknown(this)
    }

    private companion object {
        const val TAG = "GmsSmsCodeRepository"
        val PLAY_SERVICES_TIMEOUT = 500L.milliseconds
    }
}

// toSmsCodeResult is a pure function of an Intent and a Status, kept top level so it can be
// unit tested without a running Play services connection.
internal fun Intent.toSmsCodeResult(status: Status?): Result<String, SmsCodeFailure> =
    when (status?.statusCode) {
        CommonStatusCodes.SUCCESS -> {
            val code = getStringExtra(SmsCodeRetriever.EXTRA_SMS_CODE)
            if (code.isNullOrBlank())
                Result.Failure(SmsCodeFailure.Unknown(IllegalStateException("empty code")))
            else Result.Success(code)
        }

        CommonStatusCodes.TIMEOUT -> Result.Failure(SmsCodeFailure.Timeout)

        // The consent resolution can arrive here as well as on the start call, so both paths
        // have to produce ConsentRequired. Play services does not document whether this Status
        // always carries a resolution, so a missing one degrades to Unavailable.
        SmsRetrieverStatusCodes.USER_PERMISSION_REQUIRED -> {
            val intentSender = status.resolution?.intentSender
            if (intentSender == null) Result.Failure(SmsCodeFailure.Unavailable)
            else Result.Failure(SmsCodeFailure.ConsentRequired(intentSender))
        }

        else -> Result.Failure(
            SmsCodeFailure.Unknown(IllegalStateException("status=${status?.statusCode}")),
        )
    }

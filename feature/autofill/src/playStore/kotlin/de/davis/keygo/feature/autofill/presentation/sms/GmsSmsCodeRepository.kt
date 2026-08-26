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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single

@Single(binds = [SmsCodeRepository::class])
internal class GmsSmsCodeRepository(
    private val context: Context,
) : SmsCodeRepository {

    private val client by lazy {
        SmsCodeRetriever.getAutofillClient(context)
    }

    override suspend fun canOfferSuggestion(targetPackage: String): Boolean = try {
        withTimeoutOrNull(PLAY_SERVICES_TIMEOUT_MS) {
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

    // The flow below only ever produces one value. It stays a flow because awaitClose is what
    // unregisters the receiver, and first() cancels the flow on every path: a delivered code, a
    // failure, or the caller being cancelled.
    override suspend fun retrieveSmsCode(): Result<String, SmsCodeFailure> = smsCodes().first()

    private fun smsCodes(): Flow<Result<String, SmsCodeFailure>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION) return
                val status = IntentCompat.getParcelableExtra(
                    intent, SmsRetriever.EXTRA_STATUS, Status::class.java,
                )

                trySend(intent.toSmsCodeResult(status))
            }
        }

        val registered = try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION),
                SmsRetriever.SEND_PERMISSION,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not register the sms code receiver", e)
            trySend(Result.Failure(SmsCodeFailure.Unknown(e)))
            false
        }

        try {
            if (registered)
                // Start only after the receiver is live, otherwise a code that is
                // already sitting in the inbox can be delivered before you listen.
                try {
                    client.startSmsCodeRetriever().await()
                } catch (e: ResolvableApiException) {
                    // ResolvableApiException.getResolution() is annotated @NonNull, but its
                    // implementation just delegates to Status.getResolution(), which the
                    // library itself annotates @Nullable and backs with a plain field. The
                    // annotation is not honored by the implementation, so the safe call stays.
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    val intentSender = e.resolution?.intentSender
                    if (intentSender == null) trySend(Result.Failure(SmsCodeFailure.Unavailable))
                    else trySend(Result.Failure(SmsCodeFailure.ConsentRequired(intentSender)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    trySend(Result.Failure(SmsCodeFailure.Unknown(e)))
                }
        } finally {
            // awaitClose must run however the block above exits, including a rethrown
            // CancellationException, otherwise a receiver that was registered above is
            // never unregistered and leaks against the application context.
            awaitClose {
                if (registered) runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    private companion object {
        const val TAG = "GmsSmsCodeRepository"
        const val PLAY_SERVICES_TIMEOUT_MS = 500L
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

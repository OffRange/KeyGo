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
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.koin.core.annotation.Single

@Single(binds = [SmsCodeRepository::class])
internal class GmsSmsCodeRepository(
    private val context: Context,
) : SmsCodeRepository {

    private val client by lazy {
        SmsCodeRetriever.getAutofillClient(context)
    }

    override suspend fun canOfferSuggestion(targetPackage: String): Boolean = try {
        if (client.hasOngoingSmsRequest(targetPackage).await()) return false
        when (client.checkPermissionState().await()) {
            SmsCodeAutofillClient.PermissionState.GRANTED,
            SmsCodeAutofillClient.PermissionState.NONE -> true

            else -> false
        }
    } catch (e: ApiException) {
        Log.e(TAG, "SMS code autofill unavailable: ${e.statusCode}", e)
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

                trySend(intent.toResult(status))
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Start only after the receiver is live, otherwise a code that is
        // already sitting in the inbox can be delivered before you listen.
        try {
            client.startSmsCodeRetriever().await()
        } catch (e: ResolvableApiException) {
            trySend(Result.Failure(SmsCodeFailure.ConsentRequired(e.resolution.intentSender)))
        } catch (e: Exception) {
            trySend(Result.Failure(SmsCodeFailure.Unknown(e)))
        }

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun Intent.toResult(status: Status?): Result<String, SmsCodeFailure> =
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

    private companion object {
        const val TAG = "GmsSmsCodeRepository"
    }
}

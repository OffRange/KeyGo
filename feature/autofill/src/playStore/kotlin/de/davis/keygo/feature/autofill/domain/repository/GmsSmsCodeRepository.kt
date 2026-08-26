package de.davis.keygo.feature.autofill.domain.repository

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
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import de.davis.keygo.feature.autofill.domain.model.SmsCodeEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.koin.core.annotation.Single

@Single
internal class GmsSmsCodeRepository(
    private val context: Context
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

    override fun smsCodes() = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION) return
                val status = IntentCompat.getParcelableExtra(
                    intent, SmsRetriever.EXTRA_STATUS, Status::class.java
                )

                when (status?.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val code = intent.getStringExtra(SmsCodeRetriever.EXTRA_SMS_CODE)
                        trySend(
                            if (code.isNullOrBlank())
                                SmsCodeEvent.Failed(IllegalStateException("empty code"))
                            else SmsCodeEvent.SmsCodeReceived(code)
                        )
                    }

                    CommonStatusCodes.TIMEOUT -> trySend(SmsCodeEvent.Timeout)
                    else -> trySend(
                        SmsCodeEvent.Failed(IllegalStateException("status=${status?.statusCode}"))
                    )
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Start only after the receiver is live, otherwise a code that is
        // already sitting in the inbox can be delivered before you listen.
        try {
            client.startSmsCodeRetriever().await()
        } catch (e: Exception) {
            trySend(SmsCodeEvent.Failed(e))
        }

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private companion object {
        const val TAG = "GmsSmsCodeRepository"
    }
}
package de.davis.keygo.feature.credentials.presentation

import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import de.davis.keygo.feature.credentials.presentation.create.CredentialCreator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class KeyGoCredentialProviderService : CredentialProviderService() {

    private val credentialCreator by inject<CredentialCreator>()

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        val handler = CoroutineExceptionHandler { _, exception ->
            Log.w(TAG, "Error during credential creation", exception)
            callback.onError(CreateCredentialUnknownException())
        }

        val job = CoroutineScope(Dispatchers.Default + handler).launch {
            val createEntries = when (request) {
                is BeginCreatePublicKeyCredentialRequest,
                is BeginCreatePasswordCredentialRequest -> credentialCreator.create()

                else -> throw CreateCredentialUnknownException("Unsupported credential type: ${request::class.java.simpleName}")
            }.let(::listOf)

            val response = BeginCreateCredentialResponse(createEntries)
            callback.onResult(response)
        }

        cancellationSignal.setOnCancelListener {
            job.cancel()
        }
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        // TODO: Implement credential retrieval
        callback.onError(GetCredentialUnknownException())
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        Log.w(TAG, "Clearing credential state is not supported")
        callback.onError(ClearCredentialUnknownException())
    }

    companion object {
        private const val TAG = "KeyGoCredentialProviderService"
    }
}
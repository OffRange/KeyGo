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
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import de.davis.keygo.feature.credentials.di.annotation.PasskeyQualifier
import de.davis.keygo.feature.credentials.presentation.create.CredentialCreator
import de.davis.keygo.feature.credentials.presentation.provide.CredentialProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class KeyGoCredentialProviderService : CredentialProviderService() {

    private val credentialCreator by inject<CredentialCreator>()
    private val passkeyProvider by inject<CredentialProvider<BeginGetPublicKeyCredentialOption>>(
        named(PasskeyQualifier.NAMED_QUALIFIER)
    )

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        val handler = CoroutineExceptionHandler { _, exception ->
            Log.w(TAG, "Error during credential creation", exception)
            val error = exception as? CreateCredentialException
                ?: CreateCredentialUnknownException()
            callback.onError(error)
        }

        val job = CoroutineScope(Dispatchers.Default + handler).launch {
            val createEntries = when (request) {
                is BeginCreatePublicKeyCredentialRequest -> credentialCreator.create()
                is BeginCreatePasswordCredentialRequest -> throw CreateCredentialUnsupportedException(
                    "Password creation is not supported yet"
                )

                else -> throw CreateCredentialUnsupportedException("Unsupported credential type: ${request::class.java.simpleName}")
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
        val handler = CoroutineExceptionHandler { _, exception ->
            Log.w(TAG, "Error during get credential", exception)
            val error = exception as? GetCredentialException
                ?: GetCredentialUnknownException()
            callback.onError(error)
        }

        val job = CoroutineScope(Dispatchers.Default + handler).launch {
            val passkeyResults = request.beginGetCredentialOptions
                .filterIsInstance<BeginGetPublicKeyCredentialOption>()
                .flatMap { passkeyProvider.provideFor(it) }

            if (passkeyResults.isEmpty())
                throw GetCredentialUnsupportedException("No supported credential options found")

            callback.onResult(BeginGetCredentialResponse(passkeyResults))
        }

        cancellationSignal.setOnCancelListener {
            job.cancel()
        }
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
package de.davis.keygo.feature.password_health.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import de.davis.keygo.core.util.di.annotation.AppScopeQualifier
import de.davis.keygo.feature.password_health.domain.repository.ConnectivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import org.koin.core.annotation.Single

@Single
internal class ConnectivityRepositoryImpl(
    context: Context,
    @AppScopeQualifier appScope: CoroutineScope,
) : ConnectivityRepository {

    private val connectivity = requireNotNull(
        context.applicationContext.getSystemService<ConnectivityManager>()
    )

    private val internet = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                current = network
                trySend(networkCapabilities.hasInternet())
            }

            // A handover can report the old default as lost after the new one is already up.
            override fun onLost(network: Network) {
                if (network != current) return

                current = null
                trySend(false)
            }
        }

        trySend(connectivity.isCurrentlyConnected())

        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()
        // A cached value would outlive the callback that kept it current.
        .shareIn(appScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)

    override fun observeInternet(): Flow<Boolean> = internet

    private fun ConnectivityManager.isCurrentlyConnected(): Boolean =
        activeNetwork
            ?.let(::getNetworkCapabilities)
            ?.hasInternet()
            ?: false

    // Validated rules out a network that is joined but has no way out, which is where every
    // lookup would otherwise hang until its timeout.
    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

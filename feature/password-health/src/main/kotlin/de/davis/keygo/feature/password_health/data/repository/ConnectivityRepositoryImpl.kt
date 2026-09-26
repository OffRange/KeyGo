package de.davis.keygo.feature.password_health.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import de.davis.keygo.feature.password_health.domain.repository.ConnectivityRepository
import org.koin.core.annotation.Single

@Single
internal class ConnectivityRepositoryImpl(context: Context) : ConnectivityRepository {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun hasInternet(): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?: return false

        // Validated rules out a network that is joined but has no way out, which is where every
        // lookup would otherwise hang until its timeout.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

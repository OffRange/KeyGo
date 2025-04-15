package de.davis.keygo.auth.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.davis.keygo.R
import de.davis.keygo.auth.domain.model.BiometricRequest
import de.davis.keygo.auth.domain.model.BiometricResult
import de.davis.keygo.auth.domain.model.BiometricStatus
import de.davis.keygo.core.domain.Service
import de.davis.keygo.core.domain.asService
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

abstract class BiometricManager {

    var fragmentManager: FragmentManager? = null

    fun bind(lifecycle: Lifecycle, fragmentManager: FragmentManager) {
        this.fragmentManager = fragmentManager

        val observer = object : DefaultLifecycleObserver {

            override fun onDestroy(owner: LifecycleOwner) {
                this@BiometricManager.fragmentManager = null
                owner.lifecycle.removeObserver(this)
            }
        }

        lifecycle.addObserver(observer)
    }

    abstract fun getBiometricStatus(): BiometricStatus

    abstract suspend fun requestBiometric(biometricRequest: BiometricRequest): Flow<BiometricResult>
}

@Composable
fun rememberBiometricManager(): Service<BiometricManager> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val title = stringResource(id = R.string.authenticate)
    val cancel = stringResource(id = R.string.cancel)
    val biometryAuthenticator = koinInject<BiometricManager> { parametersOf(title, cancel) }

    LaunchedEffect(biometryAuthenticator, lifecycleOwner, context) {
        val fragmentManager = (context as FragmentActivity).supportFragmentManager

        biometryAuthenticator.bind(lifecycleOwner.lifecycle, fragmentManager)
    }

    return biometryAuthenticator.asService()
}
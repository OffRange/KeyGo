package de.davis.keygo.core.identity.biometric.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity

@Composable
fun BiometricPromptSupport(content: @Composable () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity
    requireNotNull(activity) { "BiometricPromptSupport must be used within a FragmentActivity context." }

    val biometricManager = remember(activity) { BiometricManager(activity) }

    CompositionLocalProvider(
        LocalBiometricManager provides biometricManager,
        content = content
    )
}

val LocalBiometricManager = staticCompositionLocalOf<BiometricManager> {
    error("No LocalBiometricManager provided")
}
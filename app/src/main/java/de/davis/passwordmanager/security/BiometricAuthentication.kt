package de.davis.passwordmanager.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import de.davis.passwordmanager.R
import de.davis.passwordmanager.utils.PreferenceUtil

object BiometricAuthentication {

    @JvmStatic
    fun auth(
        fragment: Fragment,
        callback: BiometricPrompt.AuthenticationCallback,
        negativeButtonText: String = fragment.requireContext().getString(R.string.use_password)
    ) {
        if (!isActivated(fragment.requireContext()))
            return

        val promptInfo = PromptInfo.Builder().apply {
            setTitle(fragment.getString(R.string.authentication_title))
            setDescription(fragment.getString(R.string.authentication_description))
            setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            setNegativeButtonText(negativeButtonText)
        }.build()

        val biometricPrompt = BiometricPrompt(
            fragment,
            ContextCompat.getMainExecutor(fragment.requireContext()),
            callback
        )
        biometricPrompt.authenticate(promptInfo)
    }

    @JvmStatic
    fun isAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    @JvmStatic
    fun isActivated(context: Context): Boolean {
        return isAvailable(context) && PreferenceUtil.getBoolean(
            context,
            R.string.preference_biometrics,
            false
        )
    }
}
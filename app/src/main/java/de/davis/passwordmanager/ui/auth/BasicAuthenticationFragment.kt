package de.davis.passwordmanager.ui.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.security.mainpassword.MainPassword
import de.davis.passwordmanager.ui.MainActivity

open class BasicAuthenticationFragment : Fragment() {

    protected val viewModel: AuthenticationViewModel by viewModels()

    protected lateinit var authenticationRequest: AuthenticationRequest
    protected lateinit var mainPassword: MainPassword
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            authenticationRequest = it.getParcelableCompat(
                AuthenticationActivity.KEY_AUTHENTICATION_REQUEST,
                AuthenticationRequest::class.java
            )!!
            mainPassword = it.getParcelableCompat(
                AuthenticationActivity.KEY_MAIN_PASSWORD,
                MainPassword::class.java
            )!!
        }
    }

    protected fun success() {
        val intent = Intent()

        authenticationRequest.additionalExtras?.let {
            intent.putExtras(it)
        }

        requireActivity().setResult(
            Activity.RESULT_OK,
            intent
        )

        if (requireActivity().intent.action == Intent.ACTION_MAIN)
            startActivity(Intent(requireContext(), MainActivity::class.java))
        else
            authenticationRequest.intent?.let { startActivity(it) }

        requireActivity().finish()
    }
}
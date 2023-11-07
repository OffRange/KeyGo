package de.davis.passwordmanager.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.Observer
import de.davis.passwordmanager.databinding.FragmentAuthenticateBinding
import de.davis.passwordmanager.security.BiometricAuthentication

class AuthenticateFragment : BasicAuthenticationFragment() {

    private lateinit var binding: FragmentAuthenticateBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authForm = binding.authForm
        val password = binding.passwordLayout
        val biometricBtn = binding.fingerprint
        val continueBtn = binding.continueBtn

        authForm.setTitle(authenticationRequest.message)

        biometricBtn.apply {
            visibility = if (BiometricAuthentication.isActivated(requireContext()))
                View.VISIBLE
            else
                View.GONE

            setOnClickListener { requestBiometricAuthentication() }
        }

        continueBtn.setOnClickListener {
            viewModel.authenticate(password.editText?.text.toString())
        }

        viewModel.authenticationResult.observe(viewLifecycleOwner, Observer {
            if (it is AuthenticationResult.Success) {
                password.isErrorEnabled = false

                success()
                return@Observer
            }

            val authenticationState = (it as AuthenticationResult.Error).authenticationState

            password.apply {
                isErrorEnabled = authenticationState.passwordError != null
                error = authenticationState.passwordError?.let { e -> getString(e) }
            }
        })
    }

    override fun onResume() {
        super.onResume()

        requestBiometricAuthentication()
    }

    private fun requestBiometricAuthentication() {
        BiometricAuthentication.auth(this, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                success()
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAuthenticateBinding.inflate(inflater, container, false)
        return binding.root
    }
}
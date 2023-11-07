package de.davis.passwordmanager.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.davis.passwordmanager.R
import de.davis.passwordmanager.databinding.FragmentSetMainPasswordBinding
import de.davis.passwordmanager.security.BiometricAuthentication
import de.davis.passwordmanager.security.mainpassword.MainPassword
import de.davis.passwordmanager.utils.PreferenceUtil
import kotlinx.coroutines.launch

class SetMainPasswordFragment : BasicAuthenticationFragment() {

    private lateinit var binding: FragmentSetMainPasswordBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authForm = binding.authForm

        val password = binding.passwordLayout
        val newPassword = binding.newPasswordLayout
        val confirmPassword = binding.confirmPasswordLayout

        val biometricSwitch = binding.biometricSwitch
        val continueBtn = binding.continueBtn

        authForm.setTitle(
            if (mainPassword == MainPassword.EMPTY) {
                R.string.first_steps
            } else {
                authenticationRequest.message
            }
        )

        lifecycleScope.launch {
            password.visibility =
                if (mainPassword == MainPassword.EMPTY) View.GONE else View.VISIBLE

            biometricSwitch.visibility =
                if (mainPassword == MainPassword.EMPTY && BiometricAuthentication.isAvailable(
                        requireContext()
                    )
                )
                    View.VISIBLE
                else
                    View.GONE

            newPassword.editText?.addTextChangedListener(binding.passwordStrength)

            continueBtn.setOnClickListener {
                viewModel.registerNewMainPassword(
                    password.editText?.text.toString(),
                    newPassword.editText?.text.toString(),
                    confirmPassword.editText?.text.toString()
                )

                if (biometricSwitch.visibility == View.VISIBLE) {
                    PreferenceUtil.putBoolean(
                        context,
                        R.string.preference_biometrics,
                        biometricSwitch.isChecked
                    )
                }
            }
        }

        viewModel.authenticationResult.observe(viewLifecycleOwner, Observer {
            if (it is AuthenticationResult.Success) {
                password.isErrorEnabled = false
                newPassword.isErrorEnabled = false
                confirmPassword.isErrorEnabled = false

                success()
                return@Observer
            }

            val authenticationState = (it as AuthenticationResult.Error).authenticationState

            password.apply {
                isErrorEnabled = authenticationState.passwordError != null
                error = authenticationState.passwordError?.let { e -> getString(e) }
            }

            newPassword.apply {
                isErrorEnabled = authenticationState.passwordError != null
                error = authenticationState.newPasswordError?.let { e -> getString(e) }
            }

            confirmPassword.apply {
                isErrorEnabled = authenticationState.passwordError != null
                error = authenticationState.confirmPasswordError?.let { e -> getString(e) }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSetMainPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }
}
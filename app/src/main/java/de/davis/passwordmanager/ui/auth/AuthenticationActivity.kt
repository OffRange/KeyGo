package de.davis.passwordmanager.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import de.davis.passwordmanager.R
import de.davis.passwordmanager.databinding.ActivityAuthenticationBinding
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.security.mainpassword.MainPassword
import de.davis.passwordmanager.security.mainpassword.MainPasswordManager
import de.davis.passwordmanager.ui.auth.RequestType.Companion.AUTH_REQUEST_AUTHENTICATE
import de.davis.passwordmanager.ui.auth.RequestType.Companion.AUTH_REQUEST_CHANGE_MAIN_PASSWORD
import kotlinx.coroutines.launch

class AuthenticationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthenticationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val mainPassword = MainPasswordManager.getPassword()

            val authenticationRequest = intent.extras?.getParcelableCompat(
                EXTRA_AUTH_REQUEST,
                AuthenticationRequest::class.java
            ) ?: AuthenticationRequest(
                if (mainPassword == MainPassword.EMPTY)
                    AUTH_REQUEST_CHANGE_MAIN_PASSWORD
                else
                    AUTH_REQUEST_AUTHENTICATE
            )

            when (authenticationRequest.requestType) {
                AUTH_REQUEST_AUTHENTICATE -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<AuthenticateFragment>(
                            R.id.container,
                            authenticationRequest,
                            mainPassword
                        )
                    }
                }

                AUTH_REQUEST_CHANGE_MAIN_PASSWORD -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<SetMainPasswordFragment>(
                            R.id.container,
                            authenticationRequest,
                            mainPassword
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_AUTHENTICATION_REQUEST = "authentication_request"
        const val KEY_MAIN_PASSWORD = "main_password"
    }

    inline fun <reified F : Fragment> FragmentTransaction.replace(
        @IdRes containerViewId: Int,
        authenticationRequest: AuthenticationRequest,
        mainPassword: MainPassword,
        tag: String? = null
    ): FragmentTransaction = replace(
        containerViewId, F::class.java, bundleOf(
            KEY_AUTHENTICATION_REQUEST to authenticationRequest, KEY_MAIN_PASSWORD to mainPassword
        ), tag
    )
}

fun Context.requestAuthentication(authenticationRequest: AuthenticationRequest) {
    startActivity(createRequestAuthenticationIntent(authenticationRequest))
}

fun Context.createRequestAuthenticationIntent(authenticationRequest: AuthenticationRequest): Intent {
    return Intent(this, AuthenticationActivity::class.java).apply {
        putExtra(EXTRA_AUTH_REQUEST, authenticationRequest)
    }
}
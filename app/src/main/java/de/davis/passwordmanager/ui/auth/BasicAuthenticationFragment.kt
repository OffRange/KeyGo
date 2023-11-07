package de.davis.passwordmanager.ui.auth

import android.app.Activity
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.security.mainpassword.MainPassword
import de.davis.passwordmanager.service.ParsedStructure
import de.davis.passwordmanager.service.Response
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
        var autoFill = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createAutofillResponse()?.let {
                intent.putExtra(EXTRA_AUTHENTICATION_RESULT, it)
                autoFill = true
            }
        }

        if (!autoFill)
            authenticationRequest.additionalExtras?.let { intent.putExtras(it) }

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

    @RequiresApi(Build.VERSION_CODES.O)
    fun createAutofillResponse(): FillResponse? {
        val assistStructure: AssistStructure? =
            requireActivity().intent.extras?.getParcelableCompat(
                AutofillManager.EXTRA_ASSIST_STRUCTURE,
                AssistStructure::class.java
            )

        val fillRequest: FillRequest? =
            authenticationRequest.additionalExtras?.getParcelableCompat(
                Response.EXTRA_FILL_REQUEST,
                FillRequest::class.java
            )

        if (fillRequest == null || assistStructure == null) return null

        return Response(
            requireContext(),
            ParsedStructure.parse(assistStructure, requireContext()),
            fillRequest
        ).createRealResponse()
    }
}
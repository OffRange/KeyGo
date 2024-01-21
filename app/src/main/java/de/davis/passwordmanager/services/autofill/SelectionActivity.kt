@file:RequiresApi(Build.VERSION_CODES.O)

package de.davis.passwordmanager.services.autofill

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.databinding.EmptyFragmentContainerBinding
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.services.autofill.builder.DatasetBuilder
import de.davis.passwordmanager.services.autofill.entities.AutofillField
import de.davis.passwordmanager.services.autofill.entities.AutofillForm
import de.davis.passwordmanager.services.autofill.entities.AutofillPair
import de.davis.passwordmanager.services.autofill.entities.Identifier
import de.davis.passwordmanager.services.autofill.entities.UserCredentialsType
import de.davis.passwordmanager.ui.auth.AuthenticationRequest
import de.davis.passwordmanager.ui.auth.createRequestAuthenticationIntent
import de.davis.passwordmanager.ui.dashboard.DashboardFragment

private fun AutofillField.toPair(secureElement: SecureElement) =
    when (userCredentialsType) {
        is Identifier /*All kinds of identifiers*/ -> AutofillPair(
            this,
            (secureElement.detail as PasswordDetails).username
        )

        is UserCredentialsType.Password -> AutofillPair(
            this,
            (secureElement.detail as PasswordDetails).password
        )

        else -> throw IllegalArgumentException("AutofillField can't have a field type of $userCredentialsType")
    }

class SelectionActivity : AppCompatActivity() {

    private var awaitingResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = EmptyFragmentContainerBinding.inflate(layoutInflater, null, false)
        setContentView(binding.root)

        val autofillForm = intent.extras?.getAutofillForm()

        if (autofillForm == null) {
            Toast.makeText(this, R.string.error_title, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                awaitingResult = false
                if (result.resultCode != RESULT_OK) {
                    finish()
                    return@registerForActivityResult
                }

                intent.extras?.getAutofillSelected()?.let {
                    finishWithResult(autofillForm, it)
                    return@registerForActivityResult
                }

                showSelectionUi(autofillForm)
            }
        awaitingResult = true
        launcher.launch(createRequestAuthenticationIntent(AuthenticationRequest.JUST_AUTHENTICATE))
    }

    private fun showSelectionUi(autofillForm: AutofillForm) {
        supportFragmentManager.commit {
            add(
                R.id.container,
                DashboardFragment::class.java,
                bundleOf(
                    DashboardFragment.EXTRA_DASHBOARD_HANDLER to DashboardFragment.DashboardHandler(
                        listOf(ElementType.CREDIT_CARD)
                    ) { finishWithResult(autofillForm, it) }
                )
            )
        }
    }

    private fun finishWithResult(
        autofillForm: AutofillForm,
        element: SecureElement,
        resultCode: Int = RESULT_OK
    ) {
        val autofillPairs = autofillForm.autofillFields.map { it.toPair(element) }

        val dataset = DatasetBuilder.buildDataset(
            DatasetBuilder.BuilderVariant.FillProperties(
                autofillPairs
            )
        )

        setResult(resultCode, Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        })
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (!awaitingResult)
            finish()
    }

    companion object {
        private const val EXTRA_AUTOFILL_FORM = "de.davis.passwordmanager.extra.AUTOFILL_FORM"
        private const val EXTRA_AUTOFILL_SELECTED =
            "de.davis.passwordmanager.extra.AUTOFILL_SELECTED"

        fun newIntent(
            context: Context,
            autofillForm: AutofillForm,
            selected: SecureElement? = null
        ): Intent =
            Intent(context, SelectionActivity::class.java).putExtras(
                bundleOf(
                    EXTRA_AUTOFILL_FORM to autofillForm,
                    EXTRA_AUTOFILL_SELECTED to selected
                )
            )
    }

    private fun Bundle.getAutofillForm(): AutofillForm? = getParcelableCompat(
        EXTRA_AUTOFILL_FORM, AutofillForm::class.java
    )

    private fun Bundle.getAutofillSelected(): SecureElement? = getParcelableCompat(
        EXTRA_AUTOFILL_SELECTED, SecureElement::class.java
    )
}
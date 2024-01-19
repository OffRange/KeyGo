package de.davis.passwordmanager.services.autofill

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.databinding.EmptyFragmentContainerBinding
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.manager.ActivityResultManager

class SaveActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = EmptyFragmentContainerBinding.inflate(layoutInflater, null, false)
        setContentView(binding.root)

        val secureElement = intent.extras?.getSecureElement() ?: run {
            Toast.makeText(this, R.string.error_title, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ActivityResultManager.getOrCreateManager(javaClass, this)
            .apply { registerCreate { finish() } }
            .launchCreate(secureElement, this)
    }

    companion object {
        private const val EXTRA_AUTOFILL_SAVE_ELEMENT =
            "de.davis.passwordmanager.extra.AUTOFILL_SAVE_ELEMENT"

        fun newIntent(context: Context, secureElement: SecureElement): Intent =
            Intent(context, SaveActivity::class.java).putExtra(
                EXTRA_AUTOFILL_SAVE_ELEMENT, secureElement
            )
    }

    private fun Bundle.getSecureElement(): SecureElement? = getParcelableCompat(
        EXTRA_AUTOFILL_SAVE_ELEMENT, SecureElement::class.java
    )
}
package de.davis.passwordmanager.ui.sync

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.davis.passwordmanager.backup.DataBackup.OnSyncedHandler
import de.davis.passwordmanager.backup.Result
import de.davis.passwordmanager.backup.TYPE_IMPORT
import de.davis.passwordmanager.backup.keygo.KeyGoBackup
import de.davis.passwordmanager.databinding.ActivityImportBinding
import de.davis.passwordmanager.ui.MainActivity
import de.davis.passwordmanager.ui.auth.AuthenticationRequest
import de.davis.passwordmanager.ui.auth.createRequestAuthenticationIntent
import kotlinx.coroutines.launch

class ImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityImportBinding.inflate(
            layoutInflater
        )
        setContentView(binding.root)

        val auth =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode != RESULT_OK) return@registerForActivityResult
                if (intent.action == null) return@registerForActivityResult
                if (intent.action != Intent.ACTION_VIEW) return@registerForActivityResult
                val fileUri = intent.data ?: return@registerForActivityResult

                KeyGoBackup(this).run {
                    lifecycleScope.launch {
                        execute(TYPE_IMPORT, fileUri, object : OnSyncedHandler {
                            override fun onSynced(result: Result?) {
                                startActivity(
                                    Intent(
                                        this@ImportActivity,
                                        MainActivity::class.java
                                    )
                                )
                            }
                        })
                    }
                }
            }
        val authIntent = createRequestAuthenticationIntent(AuthenticationRequest.JUST_AUTHENTICATE)
        auth.run {
            launch(authIntent)
            binding.button.setOnClickListener { _: View? -> launch(authIntent) }
        }
    }
}
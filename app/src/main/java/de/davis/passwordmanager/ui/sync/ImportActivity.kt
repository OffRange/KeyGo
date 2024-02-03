package de.davis.passwordmanager.ui.sync

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.davis.passwordmanager.backup.BackupOperation
import de.davis.passwordmanager.backup.BackupResult
import de.davis.passwordmanager.backup.impl.AndroidBackupListener
import de.davis.passwordmanager.backup.impl.AndroidPasswordProvider
import de.davis.passwordmanager.backup.impl.KdbxBackup
import de.davis.passwordmanager.backup.impl.UriStreamProvider
import de.davis.passwordmanager.databinding.ActivityImportBinding
import de.davis.passwordmanager.ui.MainActivity
import de.davis.passwordmanager.ui.auth.AuthenticationRequest
import de.davis.passwordmanager.ui.auth.createRequestAuthenticationIntent
import kotlinx.coroutines.launch

class ImportActivity : AppCompatActivity() {

    private val kdbxBackup = KdbxBackup(
        AndroidPasswordProvider(this),
        object : AndroidBackupListener(this@ImportActivity) {

            override fun onSuccess(
                backupOperation: BackupOperation,
                backupResult: BackupResult
            ) {
                super.onSuccess(backupOperation, backupResult)
                startActivity(
                    Intent(
                        this@ImportActivity,
                        MainActivity::class.java
                    )
                )
                finish()
            }
        })

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

                lifecycleScope.launch {
                    kdbxBackup.execute(
                        BackupOperation.IMPORT,
                        UriStreamProvider(fileUri, contentResolver)
                    )
                }
            }
        val authIntent = createRequestAuthenticationIntent(AuthenticationRequest.JUST_AUTHENTICATE)
        auth.run {
            launch(authIntent)
            binding.button.setOnClickListener { _: View? -> launch(authIntent) }
        }
    }
}
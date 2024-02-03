package de.davis.passwordmanager.ui.backup

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.davis.passwordmanager.PasswordManagerApplication
import de.davis.passwordmanager.R
import de.davis.passwordmanager.backup.BackupOperation
import de.davis.passwordmanager.backup.DataBackup
import de.davis.passwordmanager.backup.impl.AndroidBackupListener
import de.davis.passwordmanager.backup.impl.AndroidPasswordProvider
import de.davis.passwordmanager.backup.impl.CsvBackup
import de.davis.passwordmanager.backup.impl.KdbxBackup
import de.davis.passwordmanager.backup.impl.UriBackupResourceProvider
import de.davis.passwordmanager.ktx.getParcelableCompat
import de.davis.passwordmanager.ui.auth.AuthenticationRequest
import de.davis.passwordmanager.ui.auth.createRequestAuthenticationIntent
import kotlinx.coroutines.launch

class BackupFragment : PreferenceFragmentCompat() {

    private lateinit var kdbxBackup: KdbxBackup
    private lateinit var csvBackup: CsvBackup


    private lateinit var csvImportLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var csvExportLauncher: ActivityResultLauncher<String>

    private lateinit var kdbxImportLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var kdbxExportLauncher: ActivityResultLauncher<String>


    val auth: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
            if (result == null) return@registerForActivityResult
            val data = result.data?.extras ?: return@registerForActivityResult
            val formatType = data.getString(EXTRA_BACKUP_FORMAT) ?: return@registerForActivityResult
            val backupOperation =
                data.getParcelableCompat(EXTRA_BACKUP_TYPE, BackupOperation::class.java)
                    ?: return@registerForActivityResult

            when (backupOperation) {
                BackupOperation.EXPORT -> {
                    if (formatType == BACKUP_FORMAT_CSV) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        csvExportLauncher.launch(DEFAULT_FILE_NAME_CSV)
                    } else if (formatType == BACKUP_FORMAT_KDBX) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        kdbxExportLauncher.launch(DEFAULT_FILE_NAME_KDBX)
                    }
                }

                BackupOperation.IMPORT -> {
                    if (formatType == BACKUP_FORMAT_CSV) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        csvImportLauncher.launch(arrayOf(MIME_TYPE_CSV))
                    } else if (formatType == BACKUP_FORMAT_KDBX) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        kdbxImportLauncher.launch(arrayOf(MIME_TYPE_KDBX))
                    }
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> DataBackup.useForActivityResultRegistration(backupOperation: BackupOperation): ActivityResultLauncher<T> {
        val contract = when (backupOperation) {
            BackupOperation.IMPORT -> ActivityResultContracts.OpenDocument()
            BackupOperation.EXPORT -> {
                val mimeType = when (this) {
                    is CsvBackup -> MIME_TYPE_CSV
                    is KdbxBackup -> MIME_TYPE_KDBX
                    else -> throw IllegalStateException("Unregistered DataBackup")
                }

                ActivityResultContracts.CreateDocument(mimeType)
            }
        }

        return registerBackupLauncher(contract, this) as ActivityResultLauncher<T>
    }

    private fun <T> registerBackupLauncher(
        contract: ActivityResultContract<T, Uri?>,
        backup: DataBackup
    ): ActivityResultLauncher<T> {
        val createStreamProvider = { uri: Uri ->
            UriBackupResourceProvider(uri, requireContext().contentResolver)
        }

        return registerForActivityResult(contract) { uri: Uri? ->
            uri?.let {
                lifecycleScope.launch {
                    backup.execute(
                        if (contract is ActivityResultContracts.OpenDocument) BackupOperation.IMPORT else BackupOperation.EXPORT,
                        createStreamProvider(uri)
                    )
                }
            }
        }
    }

    private fun Preference.addLaunchAuthFunctionality(
        backupOperation: BackupOperation,
        bT: String
    ) {
        onPreferenceClickListener = Preference.OnPreferenceClickListener { _: Preference? ->
            launchAuth(backupOperation, bT)
            true
        }
    }

    private fun initiateBackupImpl() {
        AndroidBackupListener(requireContext()).also {
            kdbxBackup = KdbxBackup(AndroidPasswordProvider(requireContext()), it)
            csvBackup = CsvBackup(it)
        }
    }

    private fun initiateLaunchers() {
        initiateBackupImpl()

        csvImportLauncher = csvBackup.useForActivityResultRegistration(BackupOperation.IMPORT)
        csvExportLauncher = csvBackup.useForActivityResultRegistration(BackupOperation.EXPORT)

        kdbxImportLauncher = kdbxBackup.useForActivityResultRegistration(BackupOperation.IMPORT)
        kdbxExportLauncher = kdbxBackup.useForActivityResultRegistration(BackupOperation.EXPORT)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.backup_preferences)

        initiateLaunchers()

        findPreference<Preference>(getString(R.string.preference_import_csv))?.addLaunchAuthFunctionality(
            BackupOperation.IMPORT,
            BACKUP_FORMAT_CSV
        )
        findPreference<Preference>(getString(R.string.preference_export_csv))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                MaterialAlertDialogBuilder(
                    requireContext(),
                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
                ).apply {
                    setTitle(R.string.warning)
                    setMessage(R.string.csv_export_warning)
                    setNegativeButton(R.string.text_continue) { _: DialogInterface?, _: Int ->
                        launchAuth(BackupOperation.EXPORT, BACKUP_FORMAT_CSV)
                    }
                    setPositiveButton(R.string.use_kdbx) { _: DialogInterface?, _: Int ->
                        launchAuth(BackupOperation.EXPORT, BACKUP_FORMAT_KDBX)
                    }
                    setNeutralButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                }.show()
                true
            }

        findPreference<Preference>(getString(R.string.preference_export_kdbx))?.addLaunchAuthFunctionality(
            BackupOperation.EXPORT,
            BACKUP_FORMAT_KDBX
        )
        findPreference<Preference>(getString(R.string.preference_import_kdbx))?.addLaunchAuthFunctionality(
            BackupOperation.IMPORT,
            BACKUP_FORMAT_KDBX
        )
    }

    private fun launchAuth(backupOperation: BackupOperation, format: String) {
        auth.launch(
            requireContext().createRequestAuthenticationIntent(
                AuthenticationRequest.Builder().apply {
                    withMessage(R.string.authenticate_to_proceed)
                    withAdditionalExtras(
                        bundleOf(
                            EXTRA_BACKUP_TYPE to backupOperation,
                            EXTRA_BACKUP_FORMAT to format
                        )
                    )
                }.build()
            )
        )
    }

    companion object {
        private const val MIME_TYPE_CSV = "text/comma-separated-values"
        private const val MIME_TYPE_KDBX = "application/octet-stream"

        private const val EXTRA_BACKUP_TYPE = "de.davis.passwordmanager.extra.BACKUP_TYPE"
        private const val EXTRA_BACKUP_FORMAT = "de.davis.passwordmanager.extra.BACKUP_FORMAT"

        private const val BACKUP_FORMAT_CSV = "type_csv"
        private const val BACKUP_FORMAT_KDBX = "type_kdbx"

        private const val DEFAULT_FILE_NAME_CSV = "passwords-keygo.csv"
        private const val DEFAULT_FILE_NAME_KDBX = "elements-keygo.kdbx"
    }
}
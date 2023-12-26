package de.davis.passwordmanager.ui.backup

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.davis.passwordmanager.PasswordManagerApplication
import de.davis.passwordmanager.R
import de.davis.passwordmanager.backup.TYPE_EXPORT
import de.davis.passwordmanager.backup.TYPE_IMPORT
import de.davis.passwordmanager.backup.Type
import de.davis.passwordmanager.backup.csv.CsvBackup
import de.davis.passwordmanager.backup.keygo.KeyGoBackup
import de.davis.passwordmanager.ui.auth.AuthenticationRequest
import de.davis.passwordmanager.ui.auth.createRequestAuthenticationIntent
import kotlinx.coroutines.launch

private const val TYPE_KEYGO = "keygo"
private const val TYPE_CSV = "csv"

class BackupFragment : PreferenceFragmentCompat() {


    private lateinit var csvImportLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var csvExportLauncher: ActivityResultLauncher<String>

    private lateinit var keyGoImportLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var keyGoExportLauncher: ActivityResultLauncher<String>

    val auth: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
            if (result == null) return@registerForActivityResult
            val data = result.data?.extras ?: return@registerForActivityResult
            val formatType = data.getString("format_type") ?: return@registerForActivityResult
            when (data.getInt("type")) {
                TYPE_EXPORT -> {
                    if (formatType == TYPE_CSV) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        csvExportLauncher.launch("keygo-passwords.csv")
                    } else if (formatType == TYPE_KEYGO) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        keyGoExportLauncher.launch("elements.keygo")
                    }
                }

                TYPE_IMPORT -> {
                    if (formatType == TYPE_CSV) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        csvImportLauncher.launch(arrayOf("text/comma-separated-values"))
                    } else if (formatType == TYPE_KEYGO) {
                        (requireActivity().application as PasswordManagerApplication).disableReAuthentication()
                        keyGoImportLauncher.launch(arrayOf("application/octet-stream"))
                    }
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.backup_preferences)

        CsvBackup(requireContext()).run {
            csvImportLauncher =
                registerForActivityResult<Array<String>, Uri>(ActivityResultContracts.OpenDocument()) { result: Uri? ->
                    result?.let {
                        lifecycleScope.launch {
                            execute(TYPE_IMPORT, result)
                        }
                    }
                }

            csvExportLauncher =
                registerForActivityResult<String, Uri>(ActivityResultContracts.CreateDocument("text/comma-separated-values")) { result: Uri? ->
                    result?.let {
                        lifecycleScope.launch {
                            execute(TYPE_EXPORT, result)
                        }
                    }
                }
        }

        KeyGoBackup(requireContext()).run {
            keyGoExportLauncher =
                registerForActivityResult<String, Uri>(ActivityResultContracts.CreateDocument("application/octet-stream")) { result: Uri? ->
                    result?.let {
                        lifecycleScope.launch {
                            execute(TYPE_EXPORT, result)
                        }
                    }
                }
            keyGoImportLauncher =
                registerForActivityResult<Array<String>, Uri>(ActivityResultContracts.OpenDocument()) { result: Uri? ->
                    result?.let {
                        lifecycleScope.launch {
                            execute(TYPE_IMPORT, result)
                        }
                    }
                }
        }


        findPreference<Preference>(getString(R.string.preference_import_csv))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                launchAuth(TYPE_IMPORT, TYPE_CSV)
                true
            }

        findPreference<Preference>(getString(R.string.preference_export_csv))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                MaterialAlertDialogBuilder(
                    requireContext(),
                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
                ).apply {
                    setTitle(R.string.warning)
                    setMessage(R.string.csv_export_warning)
                    setPositiveButton(R.string.text_continue) { _: DialogInterface?, _: Int ->
                        launchAuth(
                            TYPE_EXPORT,
                            TYPE_CSV
                        )
                    }
                    setNegativeButton(R.string.use_keygo) { _: DialogInterface?, _: Int ->
                        launchAuth(
                            TYPE_EXPORT,
                            TYPE_KEYGO
                        )
                    }
                    setNeutralButton(R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                }.show()
                true
            }

        findPreference<Preference>(getString(R.string.preference_export_keygo))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                launchAuth(TYPE_EXPORT, TYPE_KEYGO)
                true
            }
        findPreference<Preference>(getString(R.string.preference_import_keygo))?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener { _: Preference? ->
                launchAuth(TYPE_IMPORT, TYPE_KEYGO)
                true
            }
    }

    private fun launchAuth(@Type type: Int, format: String) {
        auth.launch(
            requireContext().createRequestAuthenticationIntent(
                AuthenticationRequest.Builder().apply {
                    withMessage(R.string.authenticate_to_proceed)
                    withAdditionalExtras(bundleOf("type" to type, "format_type" to format))
                }.build()
            )
        )
    }
}
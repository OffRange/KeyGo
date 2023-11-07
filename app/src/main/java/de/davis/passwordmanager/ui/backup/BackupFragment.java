package de.davis.passwordmanager.ui.backup;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.backup.DataBackup;
import de.davis.passwordmanager.backup.csv.CsvBackup;
import de.davis.passwordmanager.backup.keygo.KeyGoBackup;
import de.davis.passwordmanager.ui.auth.AuthenticationActivityKt;
import de.davis.passwordmanager.ui.auth.AuthenticationRequest;

public class BackupFragment extends PreferenceFragmentCompat {

    ActivityResultLauncher<Intent> auth;

    private static final String TYPE_KEYGO = "keygo";
    private static final String TYPE_CSV = "csv";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.backup_preferences);

        ActivityResultLauncher<String[]> csvImportLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), result -> {
            CsvBackup backup = new CsvBackup(requireContext());
            backup.execute(DataBackup.TYPE_IMPORT, result);
        });

        ActivityResultLauncher<String> csvExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/comma-separated-values"), result -> {
            if(result == null)
                return;


            CsvBackup backup = new CsvBackup(requireContext());
            backup.execute(DataBackup.TYPE_EXPORT, result);
        });

        ActivityResultLauncher<String> keygoExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), result -> {
            if(result == null)
                return;

            KeyGoBackup backup = new KeyGoBackup(requireContext());
            backup.execute(DataBackup.TYPE_EXPORT, result);
        });

        ActivityResultLauncher<String[]> keygoImportLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), result -> {
            if(result == null)
                return;

            KeyGoBackup backup = new KeyGoBackup(requireContext());
            backup.execute(DataBackup.TYPE_IMPORT, result);
        });

        auth = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result == null || result.getData() == null)
                return;

            Bundle data = result.getData().getExtras();
            if(data == null)
                return;

            String formatType = data.getString("format_type");
            if(formatType == null)
                return;

            switch (data.getInt("type")){
                case DataBackup.TYPE_EXPORT -> {
                    if(formatType.equals(TYPE_CSV)){
                        ((PasswordManagerApplication)requireActivity().getApplication()).disableReAuthentication();
                        csvExportLauncher.launch("keygo-passwords.csv");
                    }else if(formatType.equals(TYPE_KEYGO)){
                        ((PasswordManagerApplication)requireActivity().getApplication()).disableReAuthentication();
                        keygoExportLauncher.launch("elements.keygo");
                    }
                }
                case DataBackup.TYPE_IMPORT -> {
                    if(formatType.equals(TYPE_CSV)){
                        ((PasswordManagerApplication)requireActivity().getApplication()).disableReAuthentication();
                        csvImportLauncher.launch(new String[]{"text/comma-separated-values"});
                    }else if(formatType.equals(TYPE_KEYGO)){
                        ((PasswordManagerApplication)requireActivity().getApplication()).disableReAuthentication();
                        keygoImportLauncher.launch(new String[]{"application/octet-stream"});
                    }
                }
            }


        });

        findPreference(getString(R.string.preference_import_csv)).setOnPreferenceClickListener(preference -> {
            launchAuth(DataBackup.TYPE_IMPORT, TYPE_CSV);
            return true;
        });

        findPreference(getString(R.string.preference_export_csv)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.csv_export_warning)
                    .setPositiveButton(R.string.text_continue,
                            (dialog, which) -> launchAuth(DataBackup.TYPE_EXPORT, TYPE_CSV))
                    .setNegativeButton(R.string.use_keygo, (dialog, which) -> launchAuth(DataBackup.TYPE_EXPORT, TYPE_KEYGO))
                    .setNeutralButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        });

        findPreference(getString(R.string.preference_export_keygo)).setOnPreferenceClickListener(preference -> {
            launchAuth(DataBackup.TYPE_EXPORT, TYPE_KEYGO);
            return true;
        });

        findPreference(getString(R.string.preference_import_keygo)).setOnPreferenceClickListener(preference -> {
            launchAuth(DataBackup.TYPE_IMPORT, TYPE_KEYGO);
            return true;
        });
    }

    public void launchAuth(@DataBackup.Type int type, String format){
        Bundle bundle = new Bundle();
        bundle.putInt("type", type);
        bundle.putString("format_type", format);
        auth.launch(AuthenticationActivityKt.createRequestAuthenticationIntent(requireContext(),
                new AuthenticationRequest.Builder()
                        .withMessage(R.string.authenticate_to_proceed)
                        .withAdditionalExtras(bundle)
                        .build()
        ));
    }
}

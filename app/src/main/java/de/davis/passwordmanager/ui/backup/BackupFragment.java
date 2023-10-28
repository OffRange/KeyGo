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
import de.davis.passwordmanager.sync.DataTransfer;
import de.davis.passwordmanager.sync.csv.CsvTransfer;
import de.davis.passwordmanager.sync.keygo.KeyGoTransfer;
import de.davis.passwordmanager.ui.login.LoginActivity;

public class BackupFragment extends PreferenceFragmentCompat {

    ActivityResultLauncher<Intent> auth;

    private static final String TYPE_KEYGO = "keygo";
    private static final String TYPE_CSV = "csv";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.backup_preferences);

        ActivityResultLauncher<String[]> csvImportLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), result -> {
            CsvTransfer transfer = new CsvTransfer(requireContext());
            transfer.start(DataTransfer.TYPE_IMPORT, result);
        });

        ActivityResultLauncher<String> csvExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/comma-separated-values"), result -> {
            if(result == null)
                return;


            CsvTransfer transfer = new CsvTransfer(requireContext());
            transfer.start(DataTransfer.TYPE_EXPORT, result);
        });

        ActivityResultLauncher<String> keygoExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), result -> {
            if(result == null)
                return;

            KeyGoTransfer transfer = new KeyGoTransfer(requireContext());
            transfer.start(DataTransfer.TYPE_EXPORT, result);
        });

        ActivityResultLauncher<String[]> keygoImportLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), result -> {
            if(result == null)
                return;

            KeyGoTransfer transfer = new KeyGoTransfer(requireContext());
            transfer.start(DataTransfer.TYPE_IMPORT, result);
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
                case DataTransfer.TYPE_EXPORT -> {
                    if(formatType.equals(TYPE_CSV)){
                        csvExportLauncher.launch("keygo-passwords.csv");
                    }else if(formatType.equals(TYPE_KEYGO)){
                        keygoExportLauncher.launch("elements.keygo");
                    }
                }
                case DataTransfer.TYPE_IMPORT -> {
                    if(formatType.equals(TYPE_CSV)){
                        ((PasswordManagerApplication)requireActivity().getApplication()).setShouldAuthenticate(false);
                        csvImportLauncher.launch(new String[]{"text/comma-separated-values"});
                    }else if(formatType.equals(TYPE_KEYGO)){
                        ((PasswordManagerApplication)requireActivity().getApplication()).setShouldAuthenticate(false);
                        keygoImportLauncher.launch(new String[]{"application/octet-stream"});
                    }
                }
            }


        });

        findPreference(getString(R.string.preference_import_csv)).setOnPreferenceClickListener(preference -> {
            launchAuth(DataTransfer.TYPE_IMPORT, TYPE_CSV);
            return true;
        });

        findPreference(getString(R.string.preference_export_csv)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.csv_export_warning)
                    .setPositiveButton(R.string.text_continue,
                            (dialog, which) -> {
                                launchAuth(DataTransfer.TYPE_EXPORT, TYPE_CSV);
                            })
                    .setNegativeButton(R.string.use_keygo, (dialog, which) -> {
                        launchAuth(DataTransfer.TYPE_EXPORT, TYPE_KEYGO);
                    })
                    .setNeutralButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        });

        findPreference(getString(R.string.preference_export_keygo)).setOnPreferenceClickListener(preference -> {
            launchAuth(DataTransfer.TYPE_EXPORT, TYPE_KEYGO);
            return true;
        });

        findPreference(getString(R.string.preference_import_keygo)).setOnPreferenceClickListener(preference -> {
            launchAuth(DataTransfer.TYPE_IMPORT, TYPE_KEYGO);
            return true;
        });
    }

    public void launchAuth(@DataTransfer.Type int type, String format){
        Bundle bundle = new Bundle();
        bundle.putInt("type", type);
        bundle.putString("format_type", format);
        auth.launch(LoginActivity.getIntentForAuthentication(requireContext()).putExtra("data", bundle));
    }
}

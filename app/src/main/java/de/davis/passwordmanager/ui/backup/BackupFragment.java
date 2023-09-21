package de.davis.passwordmanager.ui.backup;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.sync.DataTransfer;
import de.davis.passwordmanager.sync.Result;
import de.davis.passwordmanager.sync.csv.CsvTransfer;
import de.davis.passwordmanager.sync.keygo.KeyGoTransfer;

public class BackupFragment extends PreferenceFragmentCompat {

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

        findPreference(getString(R.string.preference_import_csv)).setOnPreferenceClickListener(preference -> {
            csvImportLauncher.launch(new String[]{"text/comma-separated-values"});
            return true;
        });

        findPreference(getString(R.string.preference_export_csv)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.warning)
                    .setMessage(R.string.csv_export_warning)
                    .setPositiveButton(R.string.ok,
                            (dialog, which) -> {
                                csvExportLauncher.launch("keygo-passwords.csv");
                                dialog.dismiss();
                            })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        });

        findPreference(getString(R.string.preference_export_keygo)).setOnPreferenceClickListener(preference -> {
            keygoExportLauncher.launch("elements.keygo");
            return true;
        });

        findPreference(getString(R.string.preference_import_keygo)).setOnPreferenceClickListener(preference -> {
            keygoImportLauncher.launch(new String[]{"application/octet-stream"});
            return true;
        });
    }

    private void error(Handler handler, Exception e){
        handler.post(() -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.error_title)
                .setMessage(e.getMessage())
                .setPositiveButton(R.string.ok,
                        (dialog, which) -> dialog.dismiss())
                .show());
    }

    private void handleResult(Handler handler, Result result){
        handler.post(() -> {
            if(result instanceof Result.Error error)
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.error_title)
                        .setMessage(error.getMessage())
                        .setPositiveButton(R.string.ok,
                                (dialog, which) -> dialog.dismiss())
                        .show();

            else
                Toast.makeText(requireContext(), R.string.backup_stored, Toast.LENGTH_LONG).show();
        });
    }
}

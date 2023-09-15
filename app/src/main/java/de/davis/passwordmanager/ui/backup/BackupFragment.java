package de.davis.passwordmanager.ui.backup;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.io.OutputStream;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.sync.Result;
import de.davis.passwordmanager.sync.csv.CsvExporter;
import de.davis.passwordmanager.sync.csv.CsvImporter;

public class BackupFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.backup_preferences);

        ActivityResultLauncher<String[]> launcher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), result -> {
            Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());
            doInBackground(() -> {
                try {
                    try(InputStream in = requireContext().getContentResolver().openInputStream(result)){
                        CsvImporter csvImporter = new CsvImporter(in, requireContext());
                        Result r = csvImporter.importElements();

                        handleResult(handler, r);

                    }
                } catch (Exception e) {
                    error(handler, e);
                }
            });
        });

        ActivityResultLauncher<String> launcherChooseDir = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/comma-separated-values"), result -> {
            if(result == null)
                return;

            Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());
            doInBackground(() -> {
                try {
                    try(OutputStream in = requireContext().getContentResolver().openOutputStream(result)){
                        CsvExporter csvImporter = new CsvExporter(in);
                        Result r = csvImporter.exportElements();

                        handleResult(handler, r);

                    }
                } catch (Exception e) {
                    error(handler, e);
                }
            });
        });

        findPreference(getString(R.string.preference_import_csv)).setOnPreferenceClickListener(preference -> {
            launcher.launch(new String[]{"text/comma-separated-values"});
            return true;
        });

        findPreference(getString(R.string.preference_export_csv)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.warning)
                    .setMessage(R.string.csv_export_warning)
                    .setPositiveButton(R.string.ok,
                            (dialog, which) -> {
                                launcherChooseDir.launch("keygo-passwords.csv");
                                dialog.dismiss();
                            })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .show();
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

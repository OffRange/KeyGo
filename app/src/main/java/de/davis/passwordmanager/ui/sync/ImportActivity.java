package de.davis.passwordmanager.ui.sync;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import de.davis.passwordmanager.backup.DataBackup;
import de.davis.passwordmanager.backup.keygo.KeyGoBackup;
import de.davis.passwordmanager.databinding.ActivityImportBinding;
import de.davis.passwordmanager.ui.MainActivity;
import de.davis.passwordmanager.ui.login.LoginActivity;

public class ImportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityImportBinding binding = ActivityImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ActivityResultLauncher<Intent> auth = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() != RESULT_OK)
                return;

            Intent intent = getIntent();
            if(intent == null || intent.getAction() == null)
                return;

            if (!intent.getAction().equals(Intent.ACTION_VIEW))
                return;

            Uri fileUri = intent.getData();
            if(fileUri == null)
                return;

            KeyGoBackup backup = new KeyGoBackup(this);
            backup.execute(DataBackup.TYPE_IMPORT, fileUri, r -> {
                startActivity(new Intent(this, MainActivity.class));
            });
        });

        auth.launch(LoginActivity.getIntentForAuthentication(this));
        binding.button.setOnClickListener(v -> auth.launch(LoginActivity.getIntentForAuthentication(this)));
    }
}
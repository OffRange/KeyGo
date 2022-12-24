package de.davis.passwordmanager.ui.login;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.MasterPassword;
import de.davis.passwordmanager.ui.MainActivity;

public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_AUTHENTICATION_HANDLER = "authentication_handler";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        SecureElementDatabase.createAndGet(this);

        if(getIntent().getBooleanExtra(getString(R.string.preference_authenticate_only), false)){
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new EnterPasswordFragment()).commitNow();
            return;
        }

        boolean masterPasswordAvailable = MasterPassword.getOne().blockingGet() != null;
        if(getIntent().getBooleanExtra(getString(R.string.preference_master_password), false)){
            // Executed if the user wants to change the master password
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new ChangePasswordFragment(this::finish, false)).commitNow();
        }else if(!masterPasswordAvailable){
            // Executed if the app is opened the first time
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new ChangePasswordFragment(() -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }, true)).commitNow();
        }else
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new EnterPasswordFragment()).commitNow();
    }

    public static Intent getIntentForAuthentication(@NonNull Context context){
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(context.getString(R.string.preference_authenticate_only), true);

        return intent;
    }
}
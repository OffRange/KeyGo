package de.davis.passwordmanager.ui.login;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.security.MasterPassword;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if(savedInstanceState != null)
            return;

        SecureElementDatabase.createAndGet(this);

        if(getIntent().getBooleanExtra(getString(R.string.preference_authenticate_only), false)){
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new EnterPasswordFragment()).commit();
            return;
        }

        boolean masterPasswordAvailable = MasterPassword.getOne().blockingGet() != null;
        if(getIntent().getBooleanExtra(getString(R.string.preference_master_password), false)){
            // Executed if the user wants to change the master password
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new ChangePasswordFragment()).commit();
        }else if(!masterPasswordAvailable){
            // Executed if the app is opened the first time
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new ChangePasswordFragment(true)).commit();
        }else
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new EnterPasswordFragment()).commit();
    }

    public static Intent getIntentForAuthentication(@NonNull Context context){
        return getIntentForAuthentication(context, null);
    }

    public static Intent getIntentForAuthentication(@NonNull Context context, Intent destActivity){
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(context.getString(R.string.preference_authenticate_only), true);

        if(destActivity != null)
            intent.putExtra(context.getString(R.string.authentication_destination), destActivity);

        return intent;
    }
}
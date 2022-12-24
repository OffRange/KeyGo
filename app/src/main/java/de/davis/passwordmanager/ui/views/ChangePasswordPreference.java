package de.davis.passwordmanager.ui.views;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.login.LoginActivity;

public class ChangePasswordPreference extends Preference {

    public ChangePasswordPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ChangePasswordPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ChangePasswordPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChangePasswordPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init(){
        setKey(getContext().getString(R.string.preference_master_password));
        setTitle(R.string.change_master_password);
        setIntent(new Intent(getContext(), LoginActivity.class).putExtra(getContext().getString(R.string.preference_master_password), true));
    }
}

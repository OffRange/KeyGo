package de.davis.passwordmanager.ui.settings;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.autofill.AutofillManager;
import android.widget.Toast;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricPrompt;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.Authentication;

public class SettingsFragment extends PreferenceFragmentCompat {

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        Preference fingerprint = findPreference(getString(R.string.preference_fingerprint));
        fingerprint.setVisible(Authentication.isAvailable(getContext()));
        fingerprint.setOnPreferenceChangeListener((preference, newValue) -> {
            if(!(boolean)newValue)
                return true;

            Authentication.getInstance().auth(getString(R.string.cancel), this, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);

                    ((SwitchPreference)preference).setChecked(false);
                    Toast.makeText(getContext(), errString, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                }
            });

            return true;
        });


        try {
            PackageInfo packageInfo = requireContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);

            findPreference(getString(R.string.build)).setSummary(packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        SeekBarPreference seekBarPreference = findPreference(getString(R.string.preference_reauthenticate));
        seekBarPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            setSummaryForNewAuthentication(preference, (int) newValue);
            return true;
        });
        setSummaryForNewAuthentication(seekBarPreference, seekBarPreference.getValue());
    }

    private void setSummaryForNewAuthentication(Preference preference, int newValue){
        switch (newValue){
            case 0:
                preference.setSummary(R.string.time_disabled);
                break;
            case 5:
                preference.setSummary(R.string.time_every_time);
                break;
            default:
                preference.setSummary(getResources().getQuantityString(R.plurals.time_x_minute, (int) Math.pow(2, newValue), (int)Math.pow(2, newValue)));
        }
    }

    public static long getTime(int index){
        switch (index){
            case 0:
                return -1;
            case 5:
                return Long.MAX_VALUE;
            default:
                return (long) Math.pow(2, index);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SwitchPreference autofill = findPreference(getString(R.string.preference_feature_autofill));
        if(isAutofillAvailable()){
            autofill.setChecked(isAutofillActive());

            AutofillManager afm = requireContext().getSystemService(AutofillManager.class);
            autofill.setOnPreferenceChangeListener((preference, newValue) -> {
                if(!(boolean) newValue)
                    afm.disableAutofillServices();
                else
                    startActivity(new Intent(Settings.ACTION_SETTINGS));

                return true;
            });
        }
        else autofill.getParent().setVisible(false);
    }

    @ChecksSdkIntAtLeast(api = 26)
    public boolean isAutofillAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }

        AutofillManager afm = requireContext().getSystemService(AutofillManager.class);
        return afm != null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public boolean isAutofillActive(){
        AutofillManager afm = requireContext().getSystemService(AutofillManager.class);
        return afm.hasEnabledAutofillServices();
    }
}

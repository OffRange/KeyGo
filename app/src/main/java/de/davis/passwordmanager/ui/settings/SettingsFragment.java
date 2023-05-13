package de.davis.passwordmanager.ui.settings;

import android.content.Intent;
import android.net.Uri;
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
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.Authentication;
import de.davis.passwordmanager.ui.views.UpdaterPreference;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.utils.Version;

public class SettingsFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final Uri URI = Uri.parse("https://github.com/OffRange/PasswordManager/issues/new/choose");

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

        SeekBarPreference seekBarPreference = findPreference(getString(R.string.preference_reauthenticate));
        seekBarPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            setSummaryForNewAuthentication(preference, (int) newValue);
            return true;
        });
        setSummaryForNewAuthentication(seekBarPreference, seekBarPreference.getValue());

        Preference report = findPreference(getString(R.string.report));
        CustomTabsIntent intent = new CustomTabsIntent.Builder().setShowTitle(true).build();
        intent.intent.setData(URI);
        report.setIntent(intent.intent);

        OssLicensesMenuActivity.setActivityTitle(getString(R.string.third_party_dependencies));
        findPreference(getString(R.string.preference_license)).setIntent(new Intent(getContext(), OssLicensesMenuActivity.class));

        boolean newer = Updater.getInstance().getUpdate().isNewer();
        ((UpdaterPreference)findPreference(getString(R.string.updater))).setHighlighted(newer);
        findPreference(getString(R.string.updater)).setSummary(newer
                ? getString(R.string.newer_version_available, Updater.getInstance().getUpdate().getRelease().getTagName())
                : Version.getVersion(requireContext()).getVersionName());
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

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        Fragment fragment = getParentFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if(fragment == null)
            return false;

        NavController navController = NavHostFragment.findNavController(fragment);
        navController.navigate(R.id.updaterFragment);

        return true;
    }
}

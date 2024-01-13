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
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import java.util.Objects;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.BiometricAuthentication;
import de.davis.passwordmanager.ui.LinearLayoutManager;
import de.davis.passwordmanager.version.CurrentVersion;

/**
 * A base settings fragment that displays preferences for the Android app.
 * This class is intended to be subclassed by any product flavor with the dimension "market"
 * in a class called SettingsFragment under the package de.davis.passwordmanager.ui.settings.
 *
 * <p>The BaseSettingsFragment extends PreferenceFragmentCompat and implements the
 * PreferenceFragmentCompat.OnPreferenceStartFragmentCallback interface to handle preference
 * interactions. Subclasses should override onCreatePreferences() to customize the preferences
 * displayed and onPreferenceStartFragment() to handle preference fragment navigation.
 *
 * <p>The class sets up various preferences related to authentication, autofill, and third-party dependencies.
 * It also provides methods to check for the availability and status of autofill functionality.
 *
 * <p>Subclasses can further customize the behavior and appearance of the settings fragment by
 * overriding methods such as onResume().
 */
public class BaseSettingsFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final Uri URI = Uri.parse("https://github.com/OffRange/KeyGo/issues/new/choose");

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        Preference fingerprint = findPreference(getString(R.string.preference_biometrics));
        fingerprint.setVisible(BiometricAuthentication.isAvailable(getContext()));
        fingerprint.setOnPreferenceChangeListener((preference, newValue) -> {
            if(!(boolean)newValue)
                return true;

            BiometricAuthentication.auth(this, new BiometricPrompt.AuthenticationCallback() {
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
            }, getString(R.string.cancel));

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

        Objects.requireNonNull((Preference) findPreference(getString(R.string.version)))
                .setSummary(CurrentVersion.getInstance().getVersionTag());
    }

    @NonNull
    @Override
    public RecyclerView.LayoutManager onCreateLayoutManager() {
        return new LinearLayoutManager(requireContext());
    }

    private void setSummaryForNewAuthentication(Preference preference, int newValue){
        switch (newValue) {
            case 0 -> preference.setSummary(R.string.time_disabled);
            case 5 -> preference.setSummary(R.string.time_every_time);
            default ->
                    preference.setSummary(getResources().getQuantityString(R.plurals.time_x_minute, (int) Math.pow(2, newValue), (int) Math.pow(2, newValue)));
        }
    }

    public static long getTime(int index){
        return switch (index) {
            case 0 -> -1;
            case 5 -> Long.MAX_VALUE;
            default -> (long) Math.pow(2, index);
        };
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
                    startActivity(new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:"+ requireContext().getPackageName())));

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
        return afm != null && afm.isAutofillSupported();
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

        int id = -1;
        switch (Objects.requireNonNull(pref.getFragment())){
            case "de.davis.passwordmanager.ui.settings.VersionFragment" -> id = R.id.updaterFragment;
            case "de.davis.passwordmanager.ui.backup.BackupFragment" -> id = R.id.backupFragment;
        }

        if(id == -1)
            return false;

        navController.navigate(id);

        return true;
    }
}

package de.davis.passwordmanager.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import java.util.Objects;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.version.CurrentVersion;

public class SettingsFragment extends BaseSettingsFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        Objects.requireNonNull((Preference) findPreference(getString(R.string.version)))
                .setSummary(CurrentVersion.getInstance().getVersionTag());
    }
}

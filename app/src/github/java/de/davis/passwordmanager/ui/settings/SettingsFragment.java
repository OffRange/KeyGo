package de.davis.passwordmanager.ui.settings;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import java.io.IOException;

import de.davis.passwordmanager.App;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.views.VersionPreference;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.version.CurrentVersion;

public class SettingsFragment extends BaseSettingsFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        findPreference(getString(R.string.version)).setIcon(R.drawable.ic_baseline_refresh_24);

        Updater updater = ((App)requireActivity().getApplication()).getUpdater();
        doInBackground(() -> {
            try {
                Release cached = updater.fetchByChannel(PreferenceUtil.getUpdateChannel(requireContext()));
                boolean newer = cached.isNewer();
                ((VersionPreference)findPreference(getString(R.string.version))).setHighlighted(newer);
                HandlerCompat.createAsync(Looper.getMainLooper())
                        .post(() -> findPreference(getString(R.string.version))
                                .setSummary(newer
                                        ? getString(R.string.newer_version_available, cached.getVersionTag())
                                        : CurrentVersion.getInstance().getVersionTag()));
            } catch (IOException ignored) {}
        });
    }
}

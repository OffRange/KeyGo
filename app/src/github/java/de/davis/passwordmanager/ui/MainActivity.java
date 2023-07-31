package de.davis.passwordmanager.ui;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.os.Bundle;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.navigation.NavigationBarView;

import java.io.IOException;

import de.davis.passwordmanager.App;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.utils.PreferenceUtil;

public class MainActivity extends BaseMainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Updater updater = ((App)getApplication()).getUpdater();
        doInBackground(() -> {
            try {
                Release release = updater.fetchByChannel(PreferenceUtil.getUpdateChannel(this));
                setBadge(release, findViewById(R.id.navigationView));
            } catch (IOException ignore) {}
        });
    }

    private void setBadge(Release release, NavigationBarView navigationBarView){
        BadgeDrawable badgeDrawable = navigationBarView.getOrCreateBadge(R.id.settingsFragment);
        badgeDrawable.setVisible(release.isNewer());
    }
}

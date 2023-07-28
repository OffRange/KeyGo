package de.davis.passwordmanager.ui;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.io.IOException;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.views.AddBottomSheet;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.utils.PreferenceUtil;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if(navHostFragment == null)
            return;

        NavigationBarView navigationBarView = findViewById(R.id.navigationView);
        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(navigationBarView, navController);


        getFAB().setOnClickListener(v -> new AddBottomSheet().show(getSupportFragmentManager(), "add-bottom-sheet"));

        Updater updater = ((PasswordManagerApplication)getApplication()).getUpdater();
        doInBackground(() -> {
            try {
                Release release = updater.fetchByChannel(PreferenceUtil.getUpdateChannel(this));
                setBadge(release, navigationBarView);
            } catch (IOException ignore) {}
        });


        float screenWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        if(screenWidthDp >= 600)
            return;

        var bottomSectionHandler = new BottomSectionHandler(navigationBarView, (ExtendedFloatingActionButton) getFAB(), this);
        bottomSectionHandler.handle();
    }

    private void setBadge(Release release, NavigationBarView navigationBarView){
        BadgeDrawable badgeDrawable = navigationBarView.getOrCreateBadge(R.id.settingsFragment);
        badgeDrawable.setVisible(release.isNewer());
    }

    private View getFAB(){
        float screenWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        return screenWidthDp >= 600 ? findViewById(R.id.add_rail) : findViewById(R.id.add);
    }
}
package de.davis.passwordmanager.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.settings.UpdaterFragment;
import de.davis.passwordmanager.ui.views.AddBottomSheet;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.utils.Version;

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

        Updater updater = Updater.getInstance();
        updater.setListener(new Updater.Listener() {
            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onSuccess(Updater updater, Updater.Update update) {
                BadgeDrawable badgeDrawable = navigationBarView.getOrCreateBadge(R.id.settingsFragment);
                badgeDrawable.setVisible(update.isNewer());
            }

            @Override
            public void onRunningChanged(boolean running) {}
        });
        updater.checkForGitHubRelease(Version.channelNameToType(UpdaterFragment.getChannel(this), this), this);



        float screenWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        if(screenWidthDp >= 600)
            return;

        var bottomSectionHandler = new BottomSectionHandler(navigationBarView, (ExtendedFloatingActionButton) getFAB(), this);
        bottomSectionHandler.handle();
    }

    private View getFAB(){
        float screenWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        return screenWidthDp >= 600 ? findViewById(R.id.add_rail) : findViewById(R.id.add);
    }
}
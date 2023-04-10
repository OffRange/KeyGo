package de.davis.passwordmanager.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.behavior.HideBottomViewOnScrollBehavior;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.viewmodels.ScrollingViewModel;
import de.davis.passwordmanager.ui.views.AddBottomSheet;

public class MainActivity extends AppCompatActivity {

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if(navHostFragment == null)
            return;

        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController((NavigationBarView) findViewById(R.id.navigationView), navController);


        getFAB().setOnClickListener(v -> new AddBottomSheet().show(getSupportFragmentManager(), "add-bottom-sheet"));


        float screenWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        if(screenWidthDp >= 600)
            return;

        ScrollingViewModel scrollingViewModel = new ViewModelProvider(this).get(ScrollingViewModel.class);
        scrollingViewModel.getConsumedY().observe(this, consumed -> {
            BottomNavigationView bottomNavigationView = findViewById(R.id.navigationView);
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) bottomNavigationView.getLayoutParams();
            HideBottomViewOnScrollBehavior<BottomNavigationView> behavior = (HideBottomViewOnScrollBehavior<BottomNavigationView>) params.getBehavior();
            if (behavior == null)
                return;

            ExtendedFloatingActionButton floatingActionButton = (ExtendedFloatingActionButton) getFAB();
            if(consumed < 0 && !floatingActionButton.isExtended()) {
                floatingActionButton.extend();
                behavior.slideUp(bottomNavigationView);

            }else if(consumed > 0 && floatingActionButton.isExtended()) {
                floatingActionButton.shrink();
                behavior.slideDown(bottomNavigationView);
            }
        });
    }

    private View getFAB(){
        float screenWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        return screenWidthDp >= 600 ? findViewById(R.id.add_rail) : findViewById(R.id.add);
    }
}
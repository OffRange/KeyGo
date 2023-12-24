package de.davis.passwordmanager.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.views.AddBottomSheet;

public class BaseMainActivity extends AppCompatActivity {

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

        float screenWidthDp = getResources().getConfiguration().screenWidthDp;
        if(screenWidthDp >= 600)
            return;

        var bottomSectionHandler = new BottomSectionHandler(navigationBarView, (ExtendedFloatingActionButton) getFAB(), this);
        bottomSectionHandler.handle();
    }

    private View getFAB(){
        float screenWidthDp = getResources().getConfiguration().screenWidthDp;
        return screenWidthDp >= 600 ? findViewById(R.id.add_rail) : findViewById(R.id.add);
    }
}
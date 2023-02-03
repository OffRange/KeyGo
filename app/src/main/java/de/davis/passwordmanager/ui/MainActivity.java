package de.davis.passwordmanager.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayoutMediator;

import de.davis.passwordmanager.MainFragmentStateAdapter;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final int[] TAB_NAMES = {R.string.dashboard, R.string.settings};
    private static final int[] TAB_ICONS = {R.drawable.ic_baseline_dashboard_24, R.drawable.ic_baseline_settings_24};

    private OnBackPressedListener onBackPressedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MainFragmentStateAdapter adapter = new MainFragmentStateAdapter(this);
        binding.viewPager2.setAdapter(adapter);
        binding.viewPager2.setUserInputEnabled(false);
        new TabLayoutMediator(binding.tabLayout, binding.viewPager2, (tab, position) -> {
            tab.setText(TAB_NAMES[position]);
            tab.setIcon(TAB_ICONS[position]);
        }).attach();
    }

    @Override
    public void onBackPressed() {
        if(onBackPressedListener == null || !onBackPressedListener.onBackPressed()) {
            super.onBackPressed();
        }
    }

    public void setOnBackPressedListener(OnBackPressedListener onBackPressedListener) {
        this.onBackPressedListener = onBackPressedListener;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    public interface OnBackPressedListener {
        boolean onBackPressed();
    }
}
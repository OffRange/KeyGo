package de.davis.passwordmanager;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import de.davis.passwordmanager.ui.DashboardFragment;
import de.davis.passwordmanager.ui.settings.SettingsFragment;

public class MainFragmentStateAdapter extends FragmentStateAdapter {

    public MainFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 1)
            return new SettingsFragment();

        return new DashboardFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}

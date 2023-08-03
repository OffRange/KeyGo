package de.davis.passwordmanager.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.davis.passwordmanager.databinding.FragmentUpdaterBinding;
import de.davis.passwordmanager.utils.VersionUtil;
import de.davis.passwordmanager.version.CurrentVersion;

/**
 * A basic screen that displays information about the software version.
 * This class is intended to be subclassed by any product flavor with the dimension "market"
 * in a class called VersionFragment under the package de.davis.passwordmanager.ui.settings.
 *
 * <p>The BaseVersionFragment extends Fragment and provides a layout containing views to display
 * version-related information. Subclasses should override onViewCreated() to customize the
 * information displayed.
 *
 * <p>This class uses FragmentUpdaterBinding to inflate the layout, and it sets the version tag
 * and channel information using the CurrentVersion and VersionUtil classes.
 */
public class BaseVersionFragment extends Fragment {

    protected FragmentUpdaterBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentUpdaterBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.build.setInformationText(CurrentVersion.getInstance().getVersionTag());
        binding.channel.setInformationText(VersionUtil.getChannelName(CurrentVersion.getInstance().getChannel(), requireContext()));
    }
}
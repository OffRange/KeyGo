package de.davis.passwordmanager.ui.settings;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.kohsuke.github.GHRelease;

import java.io.IOException;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.FragmentUpdaterBinding;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.utils.Version;

public class UpdaterFragment extends Fragment {

    private static final String UPDATER_PREF_KEY = "update_channel";

    private FragmentUpdaterBinding binding;

    public UpdaterFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentUpdaterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.autoComplete.setText(getChannel(requireContext()), false);
        binding.autoComplete.setOnItemClickListener((parent, view1, position, id) ->
                PreferenceUtil.getPreferences(requireContext()).edit().putString(UPDATER_PREF_KEY, (String) parent.getItemAtPosition(position)).commit());

        binding.build.setInformationText(Version.getVersion(requireContext()).getVersionName());
        binding.channel.setInformationText(Version.getVersion(requireContext()).getChannel());

        Updater updater = Updater.getInstance();
        binding.scan.setOnClickListener(v -> {
            v.setEnabled(false);
            if(updater.getUpdate().isNewer()) {
                try {
                    Updater.installRelease(requireContext());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            updater.checkForGitHubRelease(Version.channelNameToType(binding.autoComplete.getText().toString(), requireContext()), requireContext());
        });

        updater.setListener(new Updater.Listener() {
            @Override
            public void onError(Throwable throwable) {
                binding.update.setInformationText(R.string.updater_error_occured);
                binding.scan.setEnabled(true);
            }

            @Override
            public void onSuccess(Updater updater, Updater.Update update) {
                handleRelease(update);
            }

            @Override
            public void onRunningChanged(boolean running) {
                if(running)
                    binding.update.setInformationText(R.string.scanning_for_updates);
            }
        });

        boolean isFile = Updater.getVersionApkFile().isFile();
        binding.scan.setText(isFile ? R.string.install : (Updater.getInstance().getUpdate().isNewer() ? R.string.download : R.string.scan));
        binding.update.setInformationText(isFile || Updater.getInstance().getUpdate().isNewer() ? getString(R.string.newer_version_available, Updater.getInstance().getUpdate().getRelease().getTagName()) : getString(R.string.up_to_date));
    }

    private void handleRelease(Updater.Update update){
        GHRelease release = update.getRelease();
        if(release == null)
            return;

        binding.scan.setEnabled(true);
        if(!update.isNewer()) {
            binding.update.setInformationText(getString(R.string.up_to_date));
            return;
        }

        binding.update.setInformationText(getString(R.string.downloading_newer_version));
        doInBackground(() -> {
            try {
                Updater.downloadRelease(release, requireContext());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static String getChannel(Context context){
        return PreferenceUtil.getPreferences(context).getString(UPDATER_PREF_KEY, context.getResources().getStringArray(R.array.update_channels)[0]);
    }
}
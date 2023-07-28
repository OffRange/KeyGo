package de.davis.passwordmanager.ui.settings;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.apache.commons.io.FileUtils;

import java.io.IOException;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.FragmentUpdaterBinding;
import de.davis.passwordmanager.ui.viewmodels.UpdaterViewModel;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.updater.downloader.DownloadService;
import de.davis.passwordmanager.updater.exception.RateLimitException;
import de.davis.passwordmanager.updater.installer.InstallBroadcastReceiver;
import de.davis.passwordmanager.updater.version.CurrentVersion;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.utils.VersionUtil;

public class UpdaterFragment extends Fragment {

    private FragmentUpdaterBinding binding;
    private UpdaterViewModel viewModel;

    private String scanDownload;

    private Updater updater;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(action == null)
                return;

            Release release = (Release) intent.getSerializableExtra(DownloadService.EXTRA_RELEASE);
            switch (action) {
                case DownloadService.ACTION_START -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.progressBar.setIndeterminate(true);
                    binding.scan.setEnabled(false);
                    binding.update.setInformationText(R.string.init_download);
                }
                case DownloadService.ACTION_DESTROY -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.progressBar.setProgress(0);
                    binding.progressBar.setIndeterminate(true);

                    if(release == null)
                        return;

                    boolean success = intent.getBooleanExtra(DownloadService.EXTRA_SUCCESS, false);
                    if(success){
                        install(release);
                        break;
                    }

                    binding.scan.setEnabled(true);
                    binding.scan.setText(R.string.download);
                    binding.scan.setOnClickListener(v -> download(release));


                    binding.update.setInformationText(getString(R.string.newer_version_available,
                            release.getVersionTag()));

                    try {
                        FileUtils.deleteDirectory(((PasswordManagerApplication)requireActivity()
                                .getApplication()).getDownloadDir());
                    } catch (IOException ignore) {}
                }
                case DownloadService.ACTION_PROGRESS -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.progressBar.setIndeterminate(false);
                    double progress = intent.getDoubleExtra(DownloadService.EXTRA_PROGRESS, 0);
                    binding.progressBar.setProgressCompat((int) progress, true);

                    binding.scan.setEnabled(false);
                    if(!binding.scan.getText().equals(scanDownload))
                        binding.scan.setText(scanDownload);

                    if(release == null)
                        return;

                    binding.update.setInformationText(getString(R.string.downloading_newer_version,
                            release.getVersionTag()));
                }
                case InstallBroadcastReceiver.ACTION_INSTALL -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.scan.setEnabled(true);

                    ((PasswordManagerApplication)requireActivity().getApplication())
                            .setShouldAuthenticate(false);

                    if(release == null)
                        return;

                    binding.update.setInformationText(getString(R.string.newer_version_available, release.getVersionTag()));
                }
            }
        }
    };

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> permissionsRequested());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentUpdaterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        updater = ((PasswordManagerApplication)requireActivity().getApplication()).getUpdater();
        viewModel = new ViewModelProvider(requireActivity()).get(UpdaterViewModel.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askForNotificationPermission();
        }

        scanDownload = getString(R.string.download);

        binding.autoComplete.setText(VersionUtil.getChannelName(PreferenceUtil.getUpdateChannel(requireContext()), requireContext()), false);
        binding.autoComplete.setOnItemClickListener((parent, view1, position, id) ->
                    PreferenceUtil.putUpdateChannel(requireContext(),
                            VersionUtil.getChannelByName((String) parent.getItemAtPosition(position), requireContext())));

        binding.build.setInformationText(CurrentVersion.getInstance().getVersionTag());
        binding.channel.setInformationText(VersionUtil.getChannelName(CurrentVersion.getInstance().getChannel(), requireContext()));

        viewModel.getErrorLiveData().observe(getViewLifecycleOwner(), e -> {
            binding.progressBar.setVisibility(View.GONE);
            if(e instanceof RateLimitException){
                binding.scan.setEnabled(false);
                binding.scan.setText(getString(R.string.try_in_x_seconds,
                        (((RateLimitException) e).getReset() - System.currentTimeMillis() / 1000)));
                binding.update.setInformationText(R.string.gh_api_limit_exceeded);
                return;
            }

            binding.update.setInformationText(e.getMessage());
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_START);
        filter.addAction(DownloadService.ACTION_DESTROY);
        filter.addAction(DownloadService.ACTION_PROGRESS);
        filter.addAction(InstallBroadcastReceiver.ACTION_INSTALL);

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onStart() {
        super.onStart();

        ((PasswordManagerApplication)requireActivity().getApplication())
                .setShouldAuthenticate(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(broadcastReceiver);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void askForNotificationPermission(){
        if(requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED){
            permissionsRequested();
            return;
        }

        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void permissionsRequested(){
        if(!isDownloadServiceRunning())
            startFetchingIfNeeded();
    }

    private boolean isDownloadServiceRunning() {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DownloadService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void download(Release release){
        updater.download(release);
    }

    private void install(Release release){
        binding.update.setInformationText(getString(R.string.installing_newer_version, release.getVersionTag()));
        binding.scan.setEnabled(false);
        binding.progressBar.setIndeterminate(true);
        binding.progressBar.setVisibility(View.VISIBLE);

        doInBackground(() -> updater.install(release));
    }

    private void prepareUiForInstallation(Release release){
        binding.scan.setText(R.string.install);
        binding.scan.setOnClickListener(v -> install(release));
        prepareUi(release);
    }

    private void prepareUiForDownload(Release release){
        binding.scan.setText(R.string.download);
        binding.scan.setOnClickListener(v -> download(release));
        prepareUi(release);
    }

    private void prepareUi(Release release){
        binding.scan.setEnabled(true);

        if(release != null)
            binding.update.setInformationText(getString(R.string.newer_version_available, release.getVersionTag()));
    }

    private void startFetchingIfNeeded(){
        if(!updater.hasFetched()) {
            forceFetch(); //TODO maybe once a day
        }


        viewModel.getReleaseLiveData().observe(getViewLifecycleOwner(), release -> {
            binding.progressBar.setVisibility(View.GONE);
            if(release == null)
                return;

            if(!release.isNewer()) {
                binding.update.setInformationText(R.string.up_to_date);
                binding.scan.setEnabled(true);
                binding.scan.setOnClickListener(v -> forceFetch());
                binding.scan.setText(R.string.scan);
                return;
            }

            if(release.getDownloadedFile((PasswordManagerApplication) requireActivity().getApplication()).isFile()){
                prepareUiForInstallation(release);
                return;
            }

            prepareUiForDownload(release);
        });
    }

    private void forceFetch(){
        binding.progressBar.setIndeterminate(true);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.update.setInformationText(R.string.scanning_for_updates);
        binding.scan.setEnabled(false);
        viewModel.fetchGitHubReleases(PreferenceUtil.getUpdateChannel(requireContext()));
    }
}
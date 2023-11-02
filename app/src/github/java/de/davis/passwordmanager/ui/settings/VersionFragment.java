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
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.apache.commons.io.FileUtils;

import java.io.IOException;

import de.davis.passwordmanager.App;
import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.viewmodels.UpdaterViewModel;
import de.davis.passwordmanager.updater.Updater;
import de.davis.passwordmanager.updater.downloader.DownloadService;
import de.davis.passwordmanager.updater.exception.RateLimitException;
import de.davis.passwordmanager.updater.installer.InstallBroadcastReceiver;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.utils.VersionUtil;

public class VersionFragment extends BaseVersionFragment {

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
                        prepareUiForInstallation(release);
                        install(release);
                        break;
                    }

                    binding.scan.setEnabled(true);
                    binding.scan.setText(R.string.download);
                    binding.scan.setOnClickListener(v -> download(release));


                    binding.update.setInformationText(getString(R.string.newer_version_available,
                            release.getVersionTag()));

                    try {
                        FileUtils.deleteDirectory(((App)requireActivity()
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
                            .disableReAuthentication();

                    if(release == null)
                        return;

                    binding.update.setInformationText(getString(R.string.newer_version_available, release.getVersionTag()));
                }
                case Updater.ACTION_INVALID_APK -> {

                    binding.progressBar.setVisibility(View.GONE);
                    binding.scan.setEnabled(true);

                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.error_title)
                            .setMessage(R.string.invalid_apk)
                            .setPositiveButton(R.string.ok,
                                    (dialog, which) -> dialog.dismiss())
                            .show();

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

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void askForNotificationPermission(){
        if(requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED){
            permissionsRequested();
            return;
        }

        viewModel.setAskingForPermission(true);
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void permissionsRequested(){
        viewModel.setAskingForPermission(false);
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
            fetch(true); //TODO maybe once a day
        }


        viewModel.getReleaseLiveData().observe(getViewLifecycleOwner(), release -> {
            binding.progressBar.setVisibility(View.GONE);
            if(release == null)
                return;

            if(!release.isNewer()) {
                binding.update.setInformationText(R.string.up_to_date);
                binding.scan.setEnabled(true);
                binding.scan.setOnClickListener(v -> fetch(true));
                binding.scan.setText(R.string.scan);
                return;
            }

            if(release.getDownloadedFile((App) requireActivity().getApplication()).isFile()){
                prepareUiForInstallation(release);
                return;
            }

            prepareUiForDownload(release);
        });
    }

    private void fetch(boolean useCached){
        binding.progressBar.setIndeterminate(true);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.update.setInformationText(R.string.scanning_for_updates);
        binding.scan.setEnabled(false);
        viewModel.fetchGitHubReleases(PreferenceUtil.getUpdateChannel(requireContext()), useCached);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!viewModel.isAskingForPermission())
            return;
        ((PasswordManagerApplication) requireActivity().getApplication())
                .disableReAuthentication();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        updater = ((App)requireActivity().getApplication()).getUpdater();
        viewModel = new ViewModelProvider(requireActivity()).get(UpdaterViewModel.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askForNotificationPermission();
        }

        scanDownload = getString(R.string.download);

        binding.autoComplete.setText(VersionUtil.getChannelName(PreferenceUtil.getUpdateChannel(requireContext()), requireContext()), false);
        binding.autoComplete.setOnItemClickListener((parent, view1, position, id) -> {
            PreferenceUtil.putUpdateChannel(requireContext(),
                    VersionUtil.getChannelByName((String) parent.getItemAtPosition(position), requireContext()));
            fetch(false);
        });

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
        filter.addAction(Updater.ACTION_INVALID_APK);

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(broadcastReceiver, filter);
    }
}
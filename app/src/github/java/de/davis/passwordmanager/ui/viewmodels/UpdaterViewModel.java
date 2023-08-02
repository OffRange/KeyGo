package de.davis.passwordmanager.ui.viewmodels;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;

import de.davis.passwordmanager.App;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.version.Version;

public class UpdaterViewModel extends AndroidViewModel {

    private boolean isAskingForPermission;
    private final MutableLiveData<Release> releaseLiveData = new MutableLiveData<>();
    private final MutableLiveData<Exception> errorLiveData = new MutableLiveData<>();

    public UpdaterViewModel(@NonNull Application application) {
        super(application);
        fetchGitHubReleases(PreferenceUtil.getUpdateChannel(getApplication()), true);
    }

    public void fetchGitHubReleases(@Version.Channel int channel, boolean useCached) {
        doInBackground(() -> {
            try {
                releaseLiveData.postValue(((App)getApplication())
                        .getUpdater().fetchByChannel(channel, useCached));
            } catch (IOException e) {
                errorLiveData.postValue(e);
            }
        });
    }

    public MutableLiveData<Release> getReleaseLiveData() {
        return releaseLiveData;
    }

    public MutableLiveData<Exception> getErrorLiveData() {
        return errorLiveData;
    }

    public void setAskingForPermission(boolean askingForPermission) {
        isAskingForPermission = askingForPermission;
    }

    public boolean isAskingForPermission() {
        return isAskingForPermission;
    }
}

package de.davis.passwordmanager.ui.viewmodels;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;

import de.davis.passwordmanager.PasswordManagerApplication;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.updater.version.Version;
import de.davis.passwordmanager.utils.PreferenceUtil;

public class UpdaterViewModel extends AndroidViewModel {

    private final MutableLiveData<Release> releaseLiveData = new MutableLiveData<>();
    private final MutableLiveData<Exception> errorLiveData = new MutableLiveData<>();

    public UpdaterViewModel(@NonNull Application application) {
        super(application);
        fetchGitHubReleases(PreferenceUtil.getUpdateChannel(getApplication()));
    }

    public void fetchGitHubReleases(@Version.Channel int channel) {
        doInBackground(() -> {
            try {
                releaseLiveData.postValue(((PasswordManagerApplication)getApplication())
                        .getUpdater().fetchByChannel(channel));
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
}

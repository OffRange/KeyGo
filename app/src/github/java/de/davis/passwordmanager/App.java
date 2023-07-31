package de.davis.passwordmanager;

import java.io.File;

import de.davis.passwordmanager.updater.Updater;

public class App extends PasswordManagerApplication{

    private Updater updater;

    @Override
    public void onCreate() {
        super.onCreate();
        updater = new Updater(this);
    }

    public Updater getUpdater() {
        return updater;
    }

    public File getDownloadDir(){
        return new File(getCacheDir(), "apks/");
    }
}

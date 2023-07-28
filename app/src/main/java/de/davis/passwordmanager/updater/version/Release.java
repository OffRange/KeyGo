package de.davis.passwordmanager.updater.version;

import java.io.File;

import de.davis.passwordmanager.BuildConfig;
import de.davis.passwordmanager.PasswordManagerApplication;

public class Release extends Version {

    private static final long serialVersionUID = -8619273636106428792L;
    private final String assetName;

    public Release(String assetName, String tagName) {
        super(Version.fromVersionTag(tagName));
        this.assetName = assetName;
    }

    public boolean isNewer(){
        return getVersionCode() > BuildConfig.VERSION_CODE;
    }

    public File getDownloadedFile(PasswordManagerApplication application) {
        return new File(application.getDownloadDir(), getVersionCode() + ".apk");
    }

    public String getAssetName() {
        return assetName;
    }
}

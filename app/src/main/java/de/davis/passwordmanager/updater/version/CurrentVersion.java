package de.davis.passwordmanager.updater.version;

import de.davis.passwordmanager.BuildConfig;

public class CurrentVersion extends Version{

    private static final long serialVersionUID = -4800867108941482255L;
    private static CurrentVersion instance;


    private final boolean debug;

    private CurrentVersion(String vTag, boolean debug) {
        super(Version.fromVersionTag(vTag));
        this.debug = debug;
    }

    @Override
    public int getChannel() {
        return debug ? CHANNEL_UNKNOWN : super.getChannel();
    }

    public static CurrentVersion getInstance(){
        if(instance == null) {
            String vTag = "v"+ BuildConfig.VERSION_NAME;
            instance = new CurrentVersion(vTag.replaceAll("-debug", ""), BuildConfig.DEBUG);
        }

        return instance;
    }
}

package de.davis.passwordmanager.version;

import java.io.Serial;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.davis.passwordmanager.BuildConfig;

public class CurrentVersion extends Version{

    @Serial
    private static final long serialVersionUID = -4800867108941482255L;
    private static CurrentVersion instance;

    private static final String REGEX = "^(\\d+\\.\\d+\\.\\d+(?:-\\w+\\d+)?)(?:-(?!debug)(\\w+))?(-debug)?$";

    private final String additionalTag;

    private final boolean debug;

    private CurrentVersion(String vTag, String additionalTag, boolean debug) {
        super(fromVersionTag(vTag));
        this.debug = debug;
        this.additionalTag = additionalTag;
    }

    @Override
    public int getChannel() {
        return debug ? CHANNEL_UNKNOWN : super.getChannel();
    }

    @Override
    public String getVersionTag() {
        return super.getVersionTag() + (additionalTag == null ? "" : "-"+ additionalTag);
    }

    public static CurrentVersion getInstance(){
        if(instance == null) {
            Pattern pattern = Pattern.compile(REGEX);
            Matcher matcher = pattern.matcher(BuildConfig.VERSION_NAME);
            if(!matcher.matches())
                return new CurrentVersion("", null, BuildConfig.DEBUG);

            String version = "v"+ matcher.group(1);
            String additionalTag = matcher.group(2);
            String debug = matcher.group(3);

            instance = new CurrentVersion(version, additionalTag, debug != null);
        }

        return instance;
    }
}

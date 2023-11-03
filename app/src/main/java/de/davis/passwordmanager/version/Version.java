package de.davis.passwordmanager.version;

import androidx.annotation.IntDef;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version implements Serializable {

    @Serial
    private static final long serialVersionUID = -6002602362950205567L;

    public static final int CHANNEL_UNKNOWN = 0x1111;
    public static final int CHANNEL_STABLE = 0x111;
    public static final int CHANNEL_RC = 0x11;
    public static final int CHANNEL_BETA = 0x1;
    public static final int CHANNEL_ALPHA = 0x0;

    @IntDef({CHANNEL_UNKNOWN, CHANNEL_STABLE, CHANNEL_RC, CHANNEL_BETA, CHANNEL_ALPHA})
    public @interface Channel{}

    private final int major;
    private final int minor;
    private final int patch;
    private final int build;
    @Channel
    private final int channel;

    public Version(Version version){
        this(version.major, version.minor, version.patch, version.build, version.channel);
    }

    public Version(int major, int minor, int patch, int build, int channel) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.build = build;
        this.channel = channel;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public int getBuild() {
        return build;
    }

    @Channel
    public int getChannel() {
        return channel;
    }

    public String getVersionTag(){
        String versionName = "v"+ getMajor() +"."+ getMinor() +"."+ getPatch();

        int chanelBuild = Integer.bitCount(channel) * 32 + build - 1;
        String suffixBuild = String.format(Locale.getDefault(), "%02d", (chanelBuild % 32) + 1);

        switch (chanelBuild / 32) {
            case 3: // build >= 96
                break; // No suffix for release versions
            case 2: // build >= 64
                versionName += "-rc"+ suffixBuild;
                break;
            case 1: // build >= 32
                versionName += "-beta"+ suffixBuild;
                break;
            default:
                versionName += "-alpha"+ suffixBuild;
                break;
        }

        return versionName;
    }

    public long getVersionCode(){
        return major * 10_000_000L +
                minor * 10_000L +
                patch * 100L +
                Integer.bitCount(channel) * 32L + build - 1;
    }

    @Channel
    private static int getChannelByReleaseType(String releaseType){
        if(releaseType == null)
            return CHANNEL_STABLE;

        if(releaseType.equalsIgnoreCase("rc"))
            return CHANNEL_RC;

        if(releaseType.equalsIgnoreCase("beta"))
            return CHANNEL_BETA;

        if(releaseType.equalsIgnoreCase("alpha"))
            return CHANNEL_ALPHA;

        return CHANNEL_UNKNOWN;
    }

    @Channel
    public static int getChannelByVersionName(String versionName){
        if(!versionName.contains("-"))
            return CHANNEL_STABLE;

        Matcher matcher = Pattern.compile("(?<=-)[a-zA-Z]+(?=[0-9]*$)").matcher(versionName);
        if(!matcher.find())
            return CHANNEL_STABLE;

        return getChannelByReleaseType(matcher.group());
    }

    public static Version fromVersionTag(String tag){
        Pattern pattern = Pattern.compile("^v(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z]+)(\\d+))?$");
        Matcher matcher = pattern.matcher(tag);

        if (!matcher.find())
            return new Version(0, 0, 0, 0, CHANNEL_UNKNOWN);

        // Group 1: Major version
        int majorVersion = Integer.parseInt(matcher.group(1));

        // Group 2: Minor version
        int minorVersion = Integer.parseInt(matcher.group(2));

        // Group 3: Patch version
        int patchVersion = Integer.parseInt(matcher.group(3));

        // Group 4: Release type (optional)
        String releaseType = matcher.group(4);

        // Group 5: Release number (optional)
        String releaseNumber = matcher.group(5);
        int buildNumber = 1;
        if (releaseNumber != null) {
            buildNumber = Integer.parseInt(releaseNumber);
        }

        return new Version(majorVersion, minorVersion, patchVersion, buildNumber,
                getChannelByReleaseType(releaseType));
    }
}

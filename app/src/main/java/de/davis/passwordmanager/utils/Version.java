package de.davis.passwordmanager.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.IntDef;

import org.apache.commons.lang3.ArrayUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.davis.passwordmanager.R;

public class Version {

    private static Version instance;

    private final PackageInfo packageInfo;
    private final Context context;

    public static final int CHANNEL_UNKNOWN = 0x1111;
    public static final int CHANNEL_STABLE = 0x111;
    public static final int CHANNEL_RC = 0x11;
    public static final int CHANNEL_BETA = 0x1;
    public static final int CHANNEL_ALPHA = 0x0;

    private static final int[] CHANNELS = {CHANNEL_STABLE, CHANNEL_RC, CHANNEL_BETA, CHANNEL_ALPHA};

    @IntDef({CHANNEL_UNKNOWN, CHANNEL_STABLE, CHANNEL_RC, CHANNEL_BETA, CHANNEL_ALPHA})
    public @interface VersionChannel{}

    private Version(Context context){
        this.context = context;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public String getVersionName(){
        return packageInfo.versionName;
    }

    public long getVersionCode(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }

    public String getChannel(){
        return channelTypeToString(getChannelTypeByVersionName(getVersionName()), context);
    }

    public static Version getVersion(Context context){
        if(instance == null)
            instance = new Version(context);

        return instance;
    }

    @VersionChannel
    public static int getChannelTypeByVersionName(String tagName){
        if(!tagName.contains("-"))
            return CHANNEL_STABLE;

        Matcher matcher = Pattern.compile("(?<=-)[a-zA-Z]+(?=[0-9]*$)").matcher(tagName);
        if(!matcher.find())
            return CHANNEL_STABLE;

        String type = matcher.group();

        if(type.equalsIgnoreCase("rc"))
            return CHANNEL_RC;

        if(type.equalsIgnoreCase("beta"))
            return CHANNEL_BETA;

        if(type.equalsIgnoreCase("alpha"))
            return CHANNEL_ALPHA;

        return CHANNEL_UNKNOWN;
    }

    public static String channelTypeToString(@VersionChannel int channelType, Context context){
        String[] array = context.getResources().getStringArray(R.array.update_channels);
        return array[ArrayUtils.indexOf(CHANNELS, channelType)];
    }

    @VersionChannel
    public static int channelNameToType(String channelName, Context context){
        String[] array = context.getResources().getStringArray(R.array.update_channels);
        return CHANNELS[ArrayUtils.indexOf(array, channelName)];
    }
}

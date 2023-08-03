package de.davis.passwordmanager.utils;

import static de.davis.passwordmanager.version.Version.CHANNEL_ALPHA;
import static de.davis.passwordmanager.version.Version.CHANNEL_BETA;
import static de.davis.passwordmanager.version.Version.CHANNEL_RC;
import static de.davis.passwordmanager.version.Version.CHANNEL_STABLE;
import static de.davis.passwordmanager.version.Version.CHANNEL_UNKNOWN;

import android.content.Context;

import org.apache.commons.lang3.ArrayUtils;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.version.Version;

public class VersionUtil {

    private static final int[] CHANNELS = {CHANNEL_STABLE, CHANNEL_RC, CHANNEL_BETA, CHANNEL_ALPHA};

    public static String getChannelName(@Version.Channel int channelType, Context context){
        if(channelType == CHANNEL_UNKNOWN)
            return "unknown";

        String[] array = context.getResources().getStringArray(R.array.update_channels);
        return array[ArrayUtils.indexOf(CHANNELS, channelType)];
    }

    @Version.Channel
    public static int getChannelByName(String channelName, Context context){
        String[] array = context.getResources().getStringArray(R.array.update_channels);
        return CHANNELS[ArrayUtils.indexOf(array, channelName)];
    }
}

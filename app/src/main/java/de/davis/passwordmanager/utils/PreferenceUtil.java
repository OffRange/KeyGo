package de.davis.passwordmanager.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.settings.BaseSettingsFragment;
import de.davis.passwordmanager.version.Version;

public class PreferenceUtil {

    public static boolean getBoolean(Context context, @StringRes int key, boolean defValue){
        return getPreferences(context).getBoolean(context.getString(key), defValue);
    }

    public static boolean putBoolean(Context context, @StringRes int key, boolean value){
        return getPreferences(context).edit().putBoolean(context.getString(key), value).commit();
    }

    public static SharedPreferences getPreferences(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static long getTimeForNewAuthentication(Context context){
        return BaseSettingsFragment.getTime(getPreferences(context).getInt(context.getString(R.string.preference_reauthenticate), 5));
    }

    @Version.Channel
    public static int getUpdateChannel(Context context){
        return getPreferences(context).getInt("update_channel", Version.CHANNEL_STABLE);
    }

    public static void putUpdateChannel(Context context, @Version.Channel int channel){
        getPreferences(context).edit().putInt("update_channel", channel).apply();
    }
}

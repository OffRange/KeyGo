package de.davis.passwordmanager.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.StringRes;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.settings.SettingsFragment;

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
        return SettingsFragment.getTime(getPreferences(context).getInt(context.getString(R.string.preference_reauthenticate), 5));
    }
}

package de.davis.passwordmanager.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.StringRes;

public class PreferenceUtil {

    public static boolean getBoolean(Context context, @StringRes int key, boolean defValue){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(context.getString(key), defValue);
    }

    public static boolean putBoolean(Context context, @StringRes int key, boolean value){
        return getPreferences(context).edit().putBoolean(context.getString(key), value).commit();
    }

    public static SharedPreferences getPreferences(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}

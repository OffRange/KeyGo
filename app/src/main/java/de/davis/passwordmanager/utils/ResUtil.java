package de.davis.passwordmanager.utils;

import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.AnyRes;

public class ResUtil {

    @AnyRes
    public static int resolveAttribute(Resources.Theme theme, int resId){
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(resId, typedValue, true);
        return typedValue.resourceId;
    }
}

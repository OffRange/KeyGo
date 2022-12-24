package de.davis.passwordmanager.utils;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LanguageUtils {

    private static final Locale[] LOCALES = {Locale.ENGLISH, Locale.GERMAN};

    public static void iterateLocales(Context context, LocaleHandler localeHandler){
        for (Locale locale : LOCALES) {
            Configuration cfg = context.getResources().getConfiguration();
            cfg = new Configuration(cfg);
            cfg.setLocale(locale);
            localeHandler.onLocalizedContext(context.createConfigurationContext(cfg));
        }
    }

    public interface LocaleHandler {
        void onLocalizedContext(Context context);
    }
}

package de.davis.passwordmanager.utils;

import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

import java.net.URL;
import java.util.regex.Pattern;

import de.davis.passwordmanager.R;
import kotlin.text.Regex;

public class BrowserUtil {

    public static String ensureProtocol(String url){
        if(url == null)
            return null;

        if(url.matches("^(http(s?))://(.*)"))
            return url;

        return "https://"+ url;
    }

    public static Boolean couldBeUrl(String toCheck, String actualUrl){
        if (toCheck.isBlank()) return false;
        String normalizedTitle = toCheck.toLowerCase();
        String titleWithDots = Pattern.quote(normalizedTitle.replace(" ", "."));
        String titleWithoutSpaces = Pattern.quote(normalizedTitle.replace(" ", ""));

        String regexPattern = "("+ titleWithDots +"|"+ titleWithoutSpaces +")";
        try {
            URL url = new URL(actualUrl);
            String domain = url.getHost().toLowerCase();

            return new Regex(regexPattern).containsMatchIn(domain) ||
                    new Regex(regexPattern).containsMatchIn(domain.replace(".", ""));
        } catch (Exception e) {
            return false;
        }
    }

    public static void open(String url, Context context){
        url = ensureProtocol(url);

        if(!isValidURL(url)){
            Toast.makeText(context, R.string.invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        CustomTabsIntent intent = new CustomTabsIntent.Builder().setShowTitle(true).build();
        intent.intent.putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true);
        intent.launchUrl(context, Uri.parse(url));
    }

    public static boolean isValidURL(String url) {
        return URLUtil.isValidUrl(url) && url.contains(".");
    }
}

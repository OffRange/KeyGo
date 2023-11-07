package de.davis.passwordmanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amulyakhare.textdrawable.BuildConfig;
import com.google.android.material.color.DynamicColors;

import de.davis.passwordmanager.ui.auth.AuthenticationActivity;
import de.davis.passwordmanager.ui.auth.AuthenticationActivityKt;
import de.davis.passwordmanager.ui.auth.AuthenticationRequest;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.utils.TimeoutUtil;

public class PasswordManagerApplication extends Application {

    private boolean shouldAuthenticate = true;

    @SuppressLint("StaticFieldLeak")
    private static Context context;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        context = base;
    }

    public static Context getAppContext() {
        return context;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        DynamicColors.applyToActivitiesIfAvailable(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

            final TimeoutUtil timeoutUtil = new TimeoutUtil();
            Activity lastPaused;

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if(!BuildConfig.DEBUG)
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.getWindow().getDecorView().setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
                }
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {}

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if(lastPaused == null){
                    timeoutUtil.initiateDelay();
                    return;
                }

                // If activities are the same, the app was paused and a re-authentication check is
                // performed
                if(lastPaused != activity)
                    return;

                if (!shouldAuthenticate) {
                    shouldAuthenticate = true;
                    return;
                }

                if(activity instanceof AuthenticationActivity)
                    return;

                timeoutUtil.initiateDelay();
                long time = PreferenceUtil.getTimeForNewAuthentication(activity);
                if(time < 0)
                    return;

                if(time == Long.MAX_VALUE || timeoutUtil.delayMet(time * 60000)) {
                    AuthenticationActivityKt.requestAuthentication(activity, new AuthenticationRequest.Builder().withIntent(activity.getIntent()).build());
                    activity.finish();
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                lastPaused = activity;
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    public void disableReAuthentication() {
        this.shouldAuthenticate = false;
    }
}
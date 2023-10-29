package de.davis.passwordmanager;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amulyakhare.textdrawable.BuildConfig;
import com.google.android.material.color.DynamicColors;

import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.ui.login.LoginActivity;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.utils.TimeoutUtil;

public class PasswordManagerApplication extends Application {

    private boolean shouldAuthenticate;

    @Override
    public void onCreate() {
        super.onCreate();
        SecureElementDatabase.createAndGet(this);

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

                if(!shouldAuthenticate)
                    return;

                timeoutUtil.initiateDelay();
                long time = PreferenceUtil.getTimeForNewAuthentication(activity);
                if(time < 0)
                    return;

                if(time == Long.MAX_VALUE || timeoutUtil.delayMet(time * 60000)) {
                    activity.startActivity(LoginActivity.getIntentForAuthentication(activity, activity.getIntent()));
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

    public void setShouldAuthenticate(boolean shouldAuthenticate) {
        this.shouldAuthenticate = shouldAuthenticate;
    }

    public SecureElementDatabase getDatabase(){
        return SecureElementDatabase.createAndGet(this);
    }
}
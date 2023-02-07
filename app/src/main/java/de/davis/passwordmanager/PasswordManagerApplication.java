package de.davis.passwordmanager;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.DynamicColors;

import de.davis.passwordmanager.database.SecureElementDatabase;
import de.davis.passwordmanager.ui.login.LoginActivity;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.utils.TimeoutUtil;

public class PasswordManagerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

            final TimeoutUtil timeoutUtil = new TimeoutUtil();
            Activity lastPaused;

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
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

                if(lastPaused != activity)
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

    public SecureElementDatabase getDatabase(){
        return SecureElementDatabase.createAndGet(this);
    }
}

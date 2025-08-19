package de.davis.passwordmanager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.DynamicColors;

import de.davis.passwordmanager.ui.auth.AuthenticationActivity;
import de.davis.passwordmanager.ui.auth.AuthenticationActivityKt;
import de.davis.passwordmanager.ui.auth.AuthenticationRequest;
import de.davis.passwordmanager.utils.PreferenceUtil;
import de.davis.passwordmanager.utils.TimeoutUtil;

public class PasswordManagerApplication extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private boolean shouldAuthenticate = true;

    public static Context getAppContext() {
        return context;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        context = base;
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
                if (!BuildConfig.DEBUG)
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.getWindow().getDecorView().setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
                }


                WindowCompat.enableEdgeToEdge(activity.getWindow());

                ViewGroup content = activity.findViewById(android.R.id.content);
                Log.d("asas", "1");
                if (content == null)
                    return;

                View root = content.getChildAt(0);
                Log.d("asas", "2");
                root = content;

                Log.d("asas", "3");
                int initialLeft = root.getPaddingLeft();
                int initialTop = root.getPaddingTop();
                int initialRight = root.getPaddingRight();
                int initialBottom = root.getPaddingBottom();

                ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                    );

                    v.setPadding(
                            initialLeft + bars.left,
                            initialTop + bars.top,
                            initialRight + bars.right,
                            initialBottom + bars.bottom
                    );

                    return insets;
                });
                ViewCompat.requestApplyInsets(root);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (lastPaused == null) {
                    timeoutUtil.initiateDelay();
                    return;
                }

                // If activities are the same, the app was paused and a re-authentication check is
                // performed
                if (lastPaused != activity)
                    return;

                if (!shouldAuthenticate) {
                    shouldAuthenticate = true;
                    return;
                }

                if (activity instanceof AuthenticationActivity)
                    return;

                timeoutUtil.initiateDelay();
                long time = PreferenceUtil.getTimeForNewAuthentication(activity);
                if (time < 0)
                    return;

                if (time == Long.MAX_VALUE || timeoutUtil.delayMet(time * 60000)) {
                    AuthenticationActivityKt.requestAuthentication(activity, new AuthenticationRequest.Builder().withIntent(activity.getIntent()).build());
                    activity.finish();
                }
            }

            @Override
            public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                EdgeToEdgeFixKt.fix(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                lastPaused = activity;
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    public void disableReAuthentication() {
        this.shouldAuthenticate = false;
    }
}
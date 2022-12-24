package de.davis.passwordmanager.ui.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.element.password.Strength;
import de.davis.passwordmanager.utils.TimeoutUtil;

public class PasswordStrengthBar extends LinearLayout implements TextWatcher {

    private final EstimationRunnable estimationRunnable = new EstimationRunnable();

    private final TimeoutUtil timeoutUtil = new TimeoutUtil();
    private final Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());

    private LinearProgressIndicator progressIndicator;
    private TextView strengthText, strengthWarning;
    private ExecutorService executorService;

    private String lastEstimatedPassword;

    private Strength strength;

    public PasswordStrengthBar(Context context) {
        super(context);
        init();
    }

    public PasswordStrengthBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PasswordStrengthBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public PasswordStrengthBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init(){
        setOrientation(VERTICAL);

        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_password_strength_bar, this, true);

        progressIndicator = view.findViewById(R.id.progress);

        strengthText = view.findViewById(R.id.strengthText);
        strengthText.setVisibility(GONE);

        strengthWarning = view.findViewById(R.id.strengthWarning);
        strengthWarning.setVisibility(GONE);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        handler.removeCallbacks(estimationRunnable);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        update(s.toString(), false); //Needed, otherwise only updated when user stopped typing

        //This makes sure the password is really estimated as the password is only estimated
        // after a timeout (see update(String, Boolean)). If the user stops typing,
        // update(String, Boolean) is called again.
        estimationRunnable.estimatePassword = s.toString();
        handler.postDelayed(estimationRunnable, 250);
    }

    public void update(String password, boolean force){
        if(!force && !timeoutUtil.hasDelayMet(250))
            return;

        //If the last estimated password is the currently passed password, then this method should
        // not be executed again as it would waste memory.
        if(Objects.equals(lastEstimatedPassword, password))
            return;

        lastEstimatedPassword = password;

        Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());

        if(executorService != null && !executorService.isShutdown())
            executorService.shutdownNow();

        if(password.length() > 25 && !progressIndicator.isIndeterminate()) {
            progressIndicator.setIndeterminate(true);
            progressIndicator.setIndicatorColor(getContext().getColor(android.R.color.darker_gray));
        }

        executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            strength = Strength.estimateStrength(password);

            handler.post(() -> {
                progressIndicator.setIndeterminate(false);
                progressIndicator.setIndicatorColor(strength.getColor(getContext()));
                progressIndicator.setProgress(password.length() > 0 ? strength.getType() + 1 : 0);

                strengthText.setText(strength.getString());
                strengthText.setTextColor(strength.getColor(getContext()));
                strengthText.setVisibility(password.length() > 0 ? View.VISIBLE : View.GONE);

                strengthWarning.setText(strength.getWarning());
                strengthWarning.setTextColor(strength.getColor(getContext()));
                strengthWarning.setVisibility(password.length() <= 0 || strength.getWarning() == null ? GONE : VISIBLE);
            });
        });
        executorService.shutdown();
    }

    private class EstimationRunnable implements Runnable {

        private String estimatePassword;

        @Override
        public void run() {
            update(estimatePassword, true);
        }
    }
}

package de.davis.passwordmanager.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.motion.MotionUtils;
import com.google.android.material.navigation.NavigationBarView;

import de.davis.passwordmanager.ui.viewmodels.ScrollingViewModel;

public class BottomSectionHandler {

    private static final int DEFAULT_ENTER_ANIMATION_DURATION_MS = 225;
    private static final int DEFAULT_EXIT_ANIMATION_DURATION_MS = 175;
    private static final int ENTER_ANIM_DURATION_ATTR = com.google.android.material.R.attr.motionDurationLong2;
    private static final int EXIT_ANIM_DURATION_ATTR = com.google.android.material.R.attr.motionDurationMedium4;
    private static final int ENTER_EXIT_ANIM_EASING_ATTR = com.google.android.material.R.attr.motionEasingEmphasizedInterpolator;

    private final int enterAnimDuration;
    private final int exitAnimDuration;
    private final TimeInterpolator enterAnimInterpolator;
    private final TimeInterpolator exitAnimInterpolator;

    private int height = 0;

    @Nullable
    private ViewPropertyAnimator currentAnimator;

    public final View navigationBarView;
    public final ExtendedFloatingActionButton extendedFAB;

    private final ScrollingViewModel scrollingViewModel;
    private final ComponentActivity activity;

    public BottomSectionHandler(NavigationBarView navigationBarView, ExtendedFloatingActionButton extendedFAB, ComponentActivity activity) {
        this.navigationBarView = navigationBarView;
        this.extendedFAB = extendedFAB;
        this.activity = activity;

        navigationBarView.getViewTreeObserver().addOnGlobalLayoutListener(() -> height = navigationBarView.getMeasuredHeight() + ((ViewGroup.MarginLayoutParams)navigationBarView.getLayoutParams()).bottomMargin);
        enterAnimDuration = MotionUtils.resolveThemeDuration(activity, ENTER_ANIM_DURATION_ATTR, DEFAULT_ENTER_ANIMATION_DURATION_MS);
        exitAnimDuration = MotionUtils.resolveThemeDuration(activity, EXIT_ANIM_DURATION_ATTR, DEFAULT_EXIT_ANIMATION_DURATION_MS);
        enterAnimInterpolator = MotionUtils.resolveThemeInterpolator(activity, ENTER_EXIT_ANIM_EASING_ATTR, new LinearOutSlowInInterpolator());
        exitAnimInterpolator = MotionUtils.resolveThemeInterpolator(activity, ENTER_EXIT_ANIM_EASING_ATTR, new FastOutLinearInInterpolator());


        scrollingViewModel = new ViewModelProvider(activity).get(ScrollingViewModel.class);
    }

    public void handle(){
        scrollingViewModel.getFabVisibility().observe(activity, visible -> extendedFAB.setVisibility(visible ? View.VISIBLE : View.GONE));
        scrollingViewModel.getConsumedY().observe(activity, consumed -> {
            if(consumed < 0 && !extendedFAB.isExtended()) {
                extendedFAB.extend();
                slideUp(navigationBarView);

            }else if(consumed > 0 && extendedFAB.isExtended()) {
                extendedFAB.shrink();
                slideDown(navigationBarView);
            }
        });
    }

    private void slideUp(@NonNull View child) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
            child.clearAnimation();
        }

        animateChildTo(child, 0, enterAnimDuration, enterAnimInterpolator);
    }

    private void slideDown(@NonNull View child) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
            child.clearAnimation();
        }

        animateChildTo(child, height, exitAnimDuration, exitAnimInterpolator);
    }

    private void animateChildTo(@NonNull View child, int targetY, long duration, TimeInterpolator interpolator) {
        currentAnimator = child
                .animate()
                .translationY(targetY)
                .setInterpolator(interpolator)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentAnimator = null;
                    }
                });
    }
}

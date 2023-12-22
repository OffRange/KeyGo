package de.davis.passwordmanager.ui.callbacks;

import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import java.util.function.Consumer;

import de.davis.passwordmanager.ui.viewmodels.ScrollingViewModel;

public class SlidingBackPaneManager extends OnBackPressedCallback implements SlidingPaneLayout.PanelSlideListener {

    private final SlidingPaneLayout slidingPaneLayout;
    private final ScrollingViewModel scrollingViewModel;
    private Consumer<SlidingBackPaneManager> callback;

    public SlidingBackPaneManager(SlidingPaneLayout slidingPaneLayout, ScrollingViewModel scrollingViewModel) {
        super(slidingPaneLayout.isEnabled() && slidingPaneLayout.isOpen());
        this.slidingPaneLayout = slidingPaneLayout;
        this.scrollingViewModel = scrollingViewModel;

        slidingPaneLayout.addPanelSlideListener(this);
        slidingPaneLayout.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateState());
    }

    public void setUpdateStateCallback(Consumer<SlidingBackPaneManager> callback) {
        this.callback = callback;
    }

    private void updateState(){
        setEnabled(slidingPaneLayout.isEnabled() && slidingPaneLayout.isOpen());

        if(callback != null)
            callback.accept(this);
    }

    @Override
    public void handleOnBackPressed() {
        slidingPaneLayout.closePane();
        scrollingViewModel.setVisibility(true);
    }

    @Override
    public void onPanelSlide(@NonNull View panel, float slideOffset) {
        updateState();
    }

    @Override
    public void onPanelOpened(@NonNull View panel) {
        updateState();
    }

    @Override
    public void onPanelClosed(@NonNull View panel) {

    }
}

package de.davis.passwordmanager.ui.callbacks;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.google.android.material.search.SearchView;

public class SearchViewBackPressedHandler extends OnBackPressedCallback implements SearchView.TransitionListener {

    private final SearchView searchView;

    public SearchViewBackPressedHandler(SearchView searchView) {
        super(searchView.isEnabled() && searchView.isShowing());
        this.searchView = searchView;
        searchView.addTransitionListener(this);
    }

    @Override
    public void handleOnBackPressed() {
        searchView.hide();
    }

    @Override
    public void onStateChanged(@NonNull SearchView searchView, @NonNull SearchView.TransitionState previousState, @NonNull SearchView.TransitionState newState) {
        setEnabled(searchView.isEnabled() && searchView.isShowing());
    }
}

package de.davis.passwordmanager.ui.elements;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.appbar.MaterialToolbar;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementManager;
import de.davis.passwordmanager.database.dto.SecureElement;
import de.davis.passwordmanager.listeners.OnInformationChangedListener;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.ui.views.InformationView;

public abstract class ViewSecureElementFragment extends SEViewFragment {

    private MaterialToolbar toolbar;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActivityResultManager arm = ActivityResultManager.getOrCreateManager(getClass(), this);
        arm.registerEdit(this::setElement);

        InformationView titleInformationView = view.findViewById(R.id.title);
        if(titleInformationView == null)
            return;

        if(getElement() == null)
            return;

        titleInformationView.setInformationText(getElement().getTitle());
        titleInformationView.setOnChangedListener(new OnInformationChangedListener<>(getElement(), (e, changes) -> {
            getElement().setTitle(changes);
            toolbar.setTitle(changes);
            return null;
        }));
    }

    @Override
    public void fillInElement(@NonNull SecureElement e){
        SecureElementManager.updateElement(e); // Update last modified value
        toolbar = requireView().findViewById(R.id.toolbar);

        handleFavIcon(e.getFavorite());

        toolbar.setTitle(e.getTitle());
        toolbar.setSubtitle(e.getElementType().getTitle());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_fav){
                SecureElementManager.switchFavState(e);
                handleFavIcon(e.getFavorite());
                return false;
            }

            ActivityResultManager.getOrCreateManager(getClass(), null).launchEdit(e, requireContext());
            return false;
        });
        toolbar.setNavigationOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        handleNavIcon();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handleNavIcon();
    }

    private void handleNavIcon(){
        float screenWidthDp = requireActivity().getResources().getConfiguration().screenWidthDp;
        toolbar.setNavigationIcon(screenWidthDp-80 >=600 ? null : AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_close_24));
    }

    private void handleFavIcon(boolean isFavorite){
        toolbar.getMenu().findItem(R.id.menu_fav).setIcon(isFavorite ?
                R.drawable.baseline_star_24
                : R.drawable.baseline_star_outline_24);
    }
}

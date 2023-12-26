package de.davis.passwordmanager.ui.elements;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.appbar.MaterialToolbar;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementManager;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.listeners.OnInformationChangedListener;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.ui.views.InformationView;
import de.davis.passwordmanager.ui.views.TagView;

public abstract class ViewSecureElementFragment extends SEViewFragment {

    private MaterialToolbar toolbar;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActivityResultManager arm = ActivityResultManager.getOrCreateManager(getClass(), this);
        arm.registerEdit(this::setElement);

        float screenWidthDp = getResources().getConfiguration().screenWidthDp;
        if(screenWidthDp >= 600)
            return;

        float dip = 56+16*2;
        Resources r = requireContext().getResources();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, r.getDisplayMetrics());
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), (int) px);
    }

    @Override
    public void fillInElement(@NonNull SecureElement e){
        SecureElementManager.updateModifiedAt(e);
        toolbar = requireView().findViewById(R.id.toolbar);

        handleFavIcon(e.getFavorite());

        InformationView titleInformationView = requireView().findViewById(R.id.title);
        if(titleInformationView == null)
            return;

        titleInformationView.setInformationText(e.getTitle());
        titleInformationView.setOnChangedListener(new OnInformationChangedListener<>(e, (el, changes) -> {
            e.setTitle(changes);
            toolbar.setTitle(changes);
            return null;
        }));

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

        TagView tagView = requireView().findViewById(R.id.tagView);
        tagView.setTags(e.getTags());
        tagView.setOnLongClickListener(v -> {
            ActivityResultManager.getOrCreateManager(getClass(), null).launchEdit(e, requireContext());
            return true;
        });
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

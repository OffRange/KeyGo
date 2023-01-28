package de.davis.passwordmanager.ui.elements;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.listeners.OnInformationChangedListener;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementDetail;
import de.davis.passwordmanager.ui.views.InformationView;

public abstract class ViewSecureElementActivity extends SecureElementActivity {

    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityResultManager arm = ActivityResultManager.getOrCreateManager(getClass(), this);
        arm.registerEdit(changedElement -> {
            setElement(changedElement);
            recreate();
        });

        InformationView titleInformationView = getContentView().findViewById(R.id.title);
        if(titleInformationView == null)
            return;

        titleInformationView.setInformation(getElement().getTitle());
        titleInformationView.setOnChangedListener(new OnInformationChangedListener<>(getElement(), (e, changes) -> {
            getElement().setTitle(changes);
            toolbar.setTitle(changes);
            return null;
        }));
    }

    @Override
    public void recreate() {
        getIntent().putExtra(Keys.KEY_OLD, getElement());
        super.recreate();
    }

    @Override
    protected void fillInElement(@NonNull SecureElement e){
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(e.getTitle());
        toolbar.setSubtitle(SecureElementDetail.getFor(e).getTitle());
        toolbar.setOnMenuItemClickListener(item -> {
            ActivityResultManager.getOrCreateManager(getClass(), null).launchEdit(e, this);
            return false;
        });
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
}

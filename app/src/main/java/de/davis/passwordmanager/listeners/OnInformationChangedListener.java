package de.davis.passwordmanager.listeners;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.davis.passwordmanager.security.element.ElementDetail;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.ui.views.InformationView;

public class OnInformationChangedListener<E extends SecureElement> implements InformationView.OnInformationChangedListener {

    private final E element;
    private final ApplyChangeToElementHelper<E> helper;

    public OnInformationChangedListener(@NonNull E element, @NonNull ApplyChangeToElementHelper<E> helper) {
        this.element = element;
        this.helper = helper;
    }

    @Override
    public void onInformationChanged(String information) {
        ElementDetail detail = helper.applyChanges(element, information);
        if(detail != null)
            element.setDetail(detail);

        SecureElementManager.getInstance().editElement(element);
    }

    public interface ApplyChangeToElementHelper<E> {
        @Nullable
        ElementDetail applyChanges(E element, String changes);
    }
}

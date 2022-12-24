package de.davis.passwordmanager.listeners;

import androidx.annotation.NonNull;

import de.davis.passwordmanager.dialog.EditDialog;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementManager;

public class OnInformationChangedListener<E extends SecureElement> implements EditDialog.OnInformationChangeListener {

    private final E element;
    private final ApplyChangeToElementHelper<E> helper;

    public OnInformationChangedListener(@NonNull E element, @NonNull ApplyChangeToElementHelper<E> helper) {
        this.element = element;
        this.helper = helper;
    }

    @Override
    public void onInformationChanged(EditDialog dialog, String information) {
        helper.applyChanges(element, information);
        SecureElementManager.getInstance().editElement(element);
    }

    public interface ApplyChangeToElementHelper<E> {
        void applyChanges(E element, String changes);
    }
}

package de.davis.passwordmanager.listeners;

import androidx.annotation.NonNull;

import de.davis.passwordmanager.dialog.EditDialog;
import de.davis.passwordmanager.security.element.ElementDetail;
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
        //TODO maybe automate apply Function --> e.g. methode in each Element that gives available
        // tags and in this methode it checks the given tag and updates the element by itself
        ElementDetail detail = helper.applyChanges(element, information);
        if(detail != null)
            element.encrypt(detail);

        SecureElementManager.getInstance().editElement(element);
    }

    public interface ApplyChangeToElementHelper<E> {
        ElementDetail applyChanges(E element, String changes);
    }
}

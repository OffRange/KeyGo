package de.davis.passwordmanager.ui.elements;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.davis.passwordmanager.database.dtos.SecureElement;

public interface SEBaseUi {

    class Initiator{
        public static SecureElement initiate(SEBaseUi seBaseUi, Bundle bundle, String key){
            if(bundle == null || !bundle.containsKey(key))
                return null;

            Object object = bundle.getParcelable(key);
            SecureElement element = null;
            try {
                element = (SecureElement) object;
            }catch (ClassCastException ignored){}

            if(element != null)
                seBaseUi.fillInElement(element);

            return element;
        }
    }

    SecureElement getElement();

    void fillInElement(@NonNull SecureElement secureElement);
    View getContentView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);
}

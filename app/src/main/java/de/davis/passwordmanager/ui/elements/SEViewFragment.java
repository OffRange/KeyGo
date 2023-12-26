package de.davis.passwordmanager.ui.elements;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.davis.passwordmanager.database.dtos.SecureElement;

public abstract class SEViewFragment extends Fragment implements SEBaseUi {

    private SecureElement element;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = getContentView(inflater, container, savedInstanceState);
        if(view != null)
            return view;

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        element = Initiator.initiate(this, getArguments(), "element");
    }

    @Override
    public SecureElement getElement() {
        return element;
    }

    protected void setElement(SecureElement element) {
        this.element = element;
        fillInElement(element);
    }
}

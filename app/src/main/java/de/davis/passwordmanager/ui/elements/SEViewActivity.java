package de.davis.passwordmanager.ui.elements;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.database.dtos.SecureElement;

public abstract class SEViewActivity extends AppCompatActivity implements SEBaseUi {

    private SecureElement element;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentView(getLayoutInflater(), null, null));
        element = Initiator.initiate(this, getIntent().getExtras(), Keys.KEY_OLD);
    }

    @Override
    public SecureElement getElement() {
        return element;
    }
}

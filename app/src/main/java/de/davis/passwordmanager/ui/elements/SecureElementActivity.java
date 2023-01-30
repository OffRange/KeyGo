package de.davis.passwordmanager.ui.elements;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.security.element.SecureElement;

public abstract class SecureElementActivity extends AppCompatActivity {

    private SecureElement element;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentView());

        Bundle extras = getIntent().getExtras();
        if(extras == null || !extras.containsKey(Keys.KEY_OLD))
            return;

        Object object = extras.getSerializable(Keys.KEY_OLD);
        try {
            element = (SecureElement) object;
        }catch (ClassCastException ignored){}

        if(element != null)
            fillInElement(element);
    }

    public SecureElement getElement() {
        return element;
    }

    protected void setElement(SecureElement e){
        this.element = e;
    }

    protected abstract void fillInElement(@NonNull SecureElement e);
    public abstract View getContentView();
}

package de.davis.passwordmanager.listeners;

import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import de.davis.passwordmanager.text.method.CreditCardNumberTransformationMethod;
import de.davis.passwordmanager.ui.views.CheckableImageButton;

public class OnCreditCardEndIconClickListener implements View.OnClickListener {

    private final TextInputLayout textInputLayout;

    public OnCreditCardEndIconClickListener(TextInputLayout textInputLayout) {
        this.textInputLayout = textInputLayout;
    }

    @Override
    public void onClick(View v) {
        if(!(v instanceof com.google.android.material.internal.CheckableImageButton)
                && !(v instanceof CheckableImageButton))
            return;

        EditText editText = textInputLayout.getEditText();
        if (editText == null) {
            return;
        }
        // Store the current cursor position
        final int selection = editText.getSelectionEnd();
        if (editText.getTransformationMethod() instanceof PasswordTransformationMethod) {
            editText.setTransformationMethod(null);
        } else {
            editText.setTransformationMethod(CreditCardNumberTransformationMethod.getInstance());
        }
        // And restore the cursor position
        if (selection >= 0) {
            editText.setSelection(selection);
        }
        textInputLayout.refreshEndIconDrawableState();
    }
}

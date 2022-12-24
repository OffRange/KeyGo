package de.davis.passwordmanager.listeners;

import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import de.davis.passwordmanager.listeners.text.CreditCardNumberTextWatcher;

public class OnEndIconClickListener implements View.OnClickListener {

    private final TextWatcher numberWatcher = new CreditCardNumberTextWatcher();

    @Override
    public void onClick(View v) {
        if(!(v instanceof TextInputLayout))
            return;

        TextInputLayout textInputLayout = (TextInputLayout) v;
        EditText editText = textInputLayout.getEditText();
        if(editText == null)
            return;

        if (editText.getTransformationMethod() instanceof PasswordTransformationMethod) {
            editText.setTransformationMethod(null);
            setMaxInputLength(19, editText); //19 -> 16 for card number + 3 for spaces
            editText.addTextChangedListener(numberWatcher);
            editText.setText(editText.getText());
        } else {
            editText.removeTextChangedListener(numberWatcher);
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            editText.setText(editText.getText().toString().replaceAll("\\s", ""));
            setMaxInputLength(16, editText);
        }

        editText.setSelection(editText.length());

        textInputLayout.refreshEndIconDrawableState();
    }

    private void setMaxInputLength(int max, EditText editText){
        InputFilter[] filters = editText.getFilters();
        InputFilter filter = new InputFilter.LengthFilter(max);
        if(filters != null){
            for (int i = 0; i < filters.length; i++) {
                if(filters[i] instanceof InputFilter.LengthFilter){
                    filters[i] = filter;
                }
            }
        }else
            filters = new InputFilter[]{filter};

        editText.setFilters(filters);
    }
}

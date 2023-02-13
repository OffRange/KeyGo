package de.davis.passwordmanager.listeners.text;

import android.text.Editable;
import android.text.TextWatcher;

import de.davis.passwordmanager.utils.CreditCardUtil;

public class CreditCardNumberTextWatcher implements TextWatcher {

    private boolean changing;

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if(changing || s.length() == 0)
            return;

        changing = true;

        s.replace(0, s.length(), CreditCardUtil.formatNumber(s.toString()));

        changing = false;
    }
}

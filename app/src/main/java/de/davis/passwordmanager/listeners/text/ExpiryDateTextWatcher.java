package de.davis.passwordmanager.listeners.text;

import android.text.Editable;
import android.text.TextWatcher;

public class ExpiryDateTextWatcher implements TextWatcher {

    private boolean isDeleting;
    private boolean isChanging;

    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int before, int after) {
        isDeleting = before > after;
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int start, int before, int after) { }

    @Override
    public void afterTextChanged(Editable editable){
        if(isChanging || editable.length() == 0)
            return;

        isChanging = true;
        if(isDeleting){
            if(editable.length() == 2)
                editable.delete(editable.length() - 1, editable.length());
        }


        if(editable.length() >= 2){
            try{
                int month = Integer.parseInt(editable.toString().substring(0, 2));
                if(month > 12){
                    editable.delete(1, 2);
                }
            }catch (NumberFormatException ignored){}
        }

        editable.replace(0, editable.length(), format(editable.toString()));

        isChanging = false;
    }

    private String format(String s){
        return s.replace("/", "")
                .replaceAll("^([2-9])$", "0$0/")
                .replaceAll("^(([2-9])|([2-9]/\\d+))$", "0$0")
                .replaceAll("^[0-1][1-9]$", "$0/")
                .replaceAll("^([0-1][1-9])(\\d+)$", "$1/$2")
                .replaceAll("^([2-9]([1-9]))/(\\d+)$", "0$2/$3");
    }
}

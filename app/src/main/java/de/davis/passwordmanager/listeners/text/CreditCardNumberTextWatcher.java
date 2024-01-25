package de.davis.passwordmanager.listeners.text;

import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.function.Consumer;

import de.davis.passwordmanager.utils.CreditCardUtil;
import de.davis.passwordmanager.utils.card.CardType;
import de.davis.passwordmanager.utils.card.Formatter;

public class CreditCardNumberTextWatcher implements TextWatcher {

    private final EditText cardNumberEditText;
    private boolean changing;

    private Consumer<CardType> onTypeDetected;

    public CreditCardNumberTextWatcher(EditText cardNumberEditText) {
        this.cardNumberEditText = cardNumberEditText;
    }

    public void setOnTypeDetected(Consumer<CardType> onTypeDetected) {
        this.onTypeDetected = onTypeDetected;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if(changing || s.length() == 0)
            return;

        changing = true;

        CardType type = CardType.Companion.getTypeByNumber(s.toString());
        int length = type.getLengthRange().getEndInclusive();
        if(type.getFormatter() instanceof Formatter.FourDigitChunkFormatter)
            length += Math.floorDiv(length - 1, 4);
        else
            length += 2; // FourSixRemainderChunkFormatter can only add up to 2 more spaces

        String formatted = CreditCardUtil.formatNumber(s.toString());

        if(formatted.length() > length)
            formatted = formatted.substring(0, length);

        cardNumberEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
        if(onTypeDetected != null)
            onTypeDetected.accept(type);


        int selectionEnd = cardNumberEditText.getSelectionEnd();
        boolean isLast = cardNumberEditText.length() == selectionEnd;

        s.replace(0, s.length(), formatted);

        if(isLast)
            cardNumberEditText.setSelection(formatted.length());
        else
            cardNumberEditText.setSelection(Math.min(formatted.length(), selectionEnd));

        changing = false;
    }
}

package de.davis.passwordmanager.ui.elements.creditcard;

import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputLayout;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.ActivityViewCreditCardBinding;
import de.davis.passwordmanager.listeners.OnEndIconClickListener;
import de.davis.passwordmanager.listeners.OnInformationChangedListener;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.creditcard.CreditCardDetails;
import de.davis.passwordmanager.listeners.text.ExpiryDateTextWatcher;
import de.davis.passwordmanager.security.element.creditcard.Name;
import de.davis.passwordmanager.ui.elements.ViewSecureElementActivity;

public class ViewCreditCardActivity extends ViewSecureElementActivity {

    private ActivityViewCreditCardBinding binding;

    @Override
    protected void fillInElement(@NonNull SecureElement creditCard) {
        super.fillInElement(creditCard);

        CreditCardDetails details = (CreditCardDetails) creditCard.getDetail();

        binding.cardHolder.setInformation(details.getCardholder().getFullName());
        binding.cardHolder.setOnChangedListener(new OnInformationChangedListener<>(creditCard, (element, changes) -> {
            details.setCardholder(Name.fromFullName(changes));
            return details;
        }));

        binding.cardNumber.setInformation(details.getFormattedNumber());
        binding.cardNumber.getConfiguration().setInitialTextPolicy(text -> text.replace(" ", ""));
        binding.cardNumber.setOnEditDialogViewCreatedListener(view -> {
            TextInputLayout til = view.findViewById(R.id.textInputLayout);
            EditText et = til.getEditText();
            et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
            et.setKeyListener(DigitsKeyListener.getInstance("0123456789 "));
            til.setEndIconOnClickListener(new OnEndIconClickListener(til));
        });
        binding.cardNumber.setOnChangedListener(new OnInformationChangedListener<>(creditCard, (element, changes) -> {
            details.setCardNumber(changes);
            binding.cardNumber.setInformation(details.getFormattedNumber());
            return details;
        }));

        binding.cardCVV.setInformation(details.getCvv());
        binding.cardCVV.setOnChangedListener(new OnInformationChangedListener<>(creditCard, (element, changes) -> {
            details.setCvv(changes);
            return details;
        }));

        binding.expirationDate.setInformation(details.getExpirationDate());
        binding.expirationDate.setOnEditDialogViewCreatedListener(view -> {
            EditText et = ((TextInputLayout) view.findViewById(R.id.textInputLayout)).getEditText();
            et.addTextChangedListener(new ExpiryDateTextWatcher());
            et.setKeyListener(DigitsKeyListener.getInstance("0123456789/"));
        });
        binding.expirationDate.setOnChangedListener(new OnInformationChangedListener<>(creditCard, (element, changes) -> {
            details.setExpirationDate(changes);
            return details;
        }));
    }

    @Override
    public View getContentView() {
        if(binding == null)
            binding = ActivityViewCreditCardBinding.inflate(getLayoutInflater());

        return binding.getRoot();
    }
}

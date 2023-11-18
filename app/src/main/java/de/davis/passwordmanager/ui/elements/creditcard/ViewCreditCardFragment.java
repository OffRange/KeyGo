package de.davis.passwordmanager.ui.elements.creditcard;

import android.os.Bundle;
import android.text.method.DigitsKeyListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.database.entities.details.creditcard.CreditCardDetails;
import de.davis.passwordmanager.database.entities.details.creditcard.Name;
import de.davis.passwordmanager.databinding.FragmentViewCreditCardBinding;
import de.davis.passwordmanager.listeners.OnCreditCardEndIconClickListener;
import de.davis.passwordmanager.listeners.OnInformationChangedListener;
import de.davis.passwordmanager.listeners.text.CreditCardNumberTextWatcher;
import de.davis.passwordmanager.listeners.text.ExpiryDateTextWatcher;
import de.davis.passwordmanager.text.method.CreditCardNumberTransformationMethod;
import de.davis.passwordmanager.ui.elements.ViewSecureElementFragment;

public class ViewCreditCardFragment extends ViewSecureElementFragment {

    public static final int ID = R.id.viewCreditcardFragment;

    private FragmentViewCreditCardBinding binding;

    @Override
    public void fillInElement(@NonNull SecureElement creditCard) {
        super.fillInElement(creditCard);

        CreditCardDetails details = (CreditCardDetails) creditCard.getDetail();

        binding.cardHolder.setInformationText(details.getCardholder().getFullName());
        binding.cardHolder.setOnChangedListener(new OnInformationChangedListener<>(creditCard, (element, changes) -> {
            details.setCardholder(Name.fromFullName(changes));
            return details;
        }));

        binding.cardNumber.setInformationText(details.getFormattedNumber());
        binding.cardNumber.setOnViewCreatedListener(view -> {
            TextInputLayout til = view.findViewById(R.id.textInputLayout);
            EditText et = til.getEditText();
            et.setKeyListener(DigitsKeyListener.getInstance("0123456789 "));
            et.addTextChangedListener(new CreditCardNumberTextWatcher());
            til.setEndIconOnClickListener(new OnCreditCardEndIconClickListener(til));
        });
        binding.cardNumber.setTransformationMethod(CreditCardNumberTransformationMethod.getInstance());
        binding.cardNumber.setOnChangedListener(new OnInformationChangedListener<>(creditCard, (element, changes) -> {
            details.setCardNumber(changes);
            binding.cardNumber.setInformationText(details.getFormattedNumber());
            return details;
        }));

        binding.cardCVV.setInformationText(details.getCvv());
        binding.cardCVV.setOnChangedListener(new OnInformationChangedListener<>(creditCard, (element, changes) -> {
            details.setCvv(changes);
            return details;
        }));

        binding.expirationDate.setInformationText(details.getExpirationDate());
        binding.expirationDate.setOnViewCreatedListener(view -> {
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
    public View getContentView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if(binding == null)
            binding = FragmentViewCreditCardBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }
}

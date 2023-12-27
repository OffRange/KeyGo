package de.davis.passwordmanager.ui.elements.creditcard;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.drawable.DrawableCompat;

import com.github.devnied.emvnfccard.exception.CommunicationException;
import com.github.devnied.emvnfccard.model.EmvCard;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.database.entities.details.creditcard.CreditCardDetails;
import de.davis.passwordmanager.database.entities.details.creditcard.Name;
import de.davis.passwordmanager.databinding.ActivityCreateCreditcardBinding;
import de.davis.passwordmanager.listeners.OnCreditCardEndIconClickListener;
import de.davis.passwordmanager.listeners.text.CreditCardNumberTextWatcher;
import de.davis.passwordmanager.listeners.text.ExpiryDateTextWatcher;
import de.davis.passwordmanager.nfc.NfcManager;
import de.davis.passwordmanager.text.method.CreditCardNumberTransformationMethod;
import de.davis.passwordmanager.ui.elements.CreateSecureElementActivity;
import de.davis.passwordmanager.utils.CreditCardUtil;

public class CreateCreditCardActivity extends CreateSecureElementActivity {

    private ActivityCreateCreditcardBinding binding;
    private NfcManager nfcManager;
    private Timer timer;

    @ColorInt
    private int dColor;
    @ColorInt
    private int colorRed;
    @ColorInt
    private int colorYellow;
    @ColorInt
    private int colorGreen;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.BLACK);
        colorRed = MaterialColors.getColor(this, R.attr.colorWeak, Color.BLACK);
        colorYellow = MaterialColors.getColor(this, R.attr.colorModerate, Color.BLACK);
        colorGreen = MaterialColors.getColor(this, R.attr.colorVeryStrong, Color.BLACK);

        Objects.requireNonNull(binding.textInputLayoutCardDate.getEditText()).addTextChangedListener(new ExpiryDateTextWatcher());

        Objects.requireNonNull(binding.textInputLayoutCardNumber.getEditText()).addTextChangedListener(new CreditCardNumberTextWatcher());
        binding.textInputLayoutCardNumber.getEditText().setTransformationMethod(CreditCardNumberTransformationMethod.getInstance());
        binding.textInputLayoutCardNumber.setEndIconOnClickListener(new OnCreditCardEndIconClickListener(binding.textInputLayoutCardNumber));

        nfcManager = new NfcManager(this) {
            @Override
            protected void cardReceived(EmvCard card, CommunicationException e) {
                if(e != null){
                    setNfcMessageError(R.string.nfc_moved_too_fast);
                    return;
                }

                if(card == null){
                    setNfcMessageError(R.string.nfc_unknown_card);
                    return;
                }

                switch (card.getState()) {
                    case UNKNOWN -> setNfcMessageError(R.string.nfc_unknown_card);
                    case LOCKED -> setNfcMessageError(R.string.nfc_locked);
                    case ACTIVE -> {
                        insertCard(card);
                        setNfcMessageSuccess(R.string.nfc_success);
                    }
                    default -> {
                    }
                }
            }
        };
    }

    @Override
    public void fillInElement(@NonNull SecureElement element) {
        super.fillInElement(element);
        binding.textInputLayoutTitle.getEditText().setText(element.getTitle());

        CreditCardDetails details = (CreditCardDetails) element.getDetail();
        binding.textInputLayoutUsername.getEditText().setText(details.getCardholder().getFullName());
        binding.textInputLayoutCardNumber.getEditText().setText(details.getCardNumber());
        binding.textInputLayoutCardCVV.getEditText().setText(details.getCvv());
        binding.textInputLayoutCardDate.getEditText().setText(details.getExpirationDate());
    }

    @Override
    public View getContentView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if(binding == null)
            binding = ActivityCreateCreditcardBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!nfcManager.isAvailable()){
            binding.nfc.setVisibility(View.GONE);
            return;
        }

        if (nfcManager.isEnabled()) {
            binding.nfc.setInformationText(R.string.nfc_listening);
            binding.nfc.setInformationTextColor(dColor);
            binding.nfc.getIconDrawable().setTint(dColor);
            nfcManager.enable();
            return;
        }

        Snackbar.make(binding.getRoot(), R.string.nfc_enable, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.enable, view -> {
                    Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                    startActivity(intent);
                })
                .show();

        binding.nfc.setInformationText(R.string.nfc_enable);
        binding.nfc.setInformationTextColor(colorYellow);
        binding.nfc.getIconDrawable().setTint(colorYellow);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcManager.disable();
    }

    @Override
    public CreateSecureElementActivity.Result check() {
        Result result = new Result();
        result.setSuccess(true);

        String title = Objects.requireNonNull(binding.textInputLayoutTitle.getEditText()).getText().toString();
        String creditCardNumber = Objects.requireNonNull(binding.textInputLayoutCardNumber.getEditText()).getText().toString();
        String expiryDate = Objects.requireNonNull(binding.textInputLayoutCardDate.getEditText()).getText().toString();

        if(title.isBlank()){
            binding.textInputLayoutTitle.setError(getString(R.string.is_not_filled_in));
            result.setSuccess(false);
        }else
            binding.textInputLayoutTitle.setErrorEnabled(false);

        if(creditCardNumber.isBlank()){
            binding.textInputLayoutCardNumber.setError(getString(R.string.is_not_filled_in));
            result.setSuccess(false);
        }else
            binding.textInputLayoutCardNumber.setErrorEnabled(false);

        if(!CreditCardUtil.isValidCardNumberLength(creditCardNumber)){
            binding.textInputLayoutCardNumber.setError(getString(R.string.invalid_card_number_length));
            result.setSuccess(false);
        }else
            binding.textInputLayoutCardNumber.setErrorEnabled(false);

        if(!expiryDate.isBlank() && !CreditCardUtil.isValidDateFormat(expiryDate)){
            binding.textInputLayoutCardDate.setError(getString(R.string.invalid_date));
            result.setSuccess(false);
        }else
            binding.textInputLayoutCardDate.setErrorEnabled(false);

        if(!result.isSuccess())
            return result;


        result.setElement(toElement());
        return result;
    }

    @Override
    protected SecureElement toElement() {
        String title = Objects.requireNonNull(binding.textInputLayoutTitle.getEditText()).getText().toString().trim();
        String creditCardNumber = Objects.requireNonNull(binding.textInputLayoutCardNumber.getEditText()).getText().toString().trim();
        String expiryDate = Objects.requireNonNull(binding.textInputLayoutCardDate.getEditText()).getText().toString().trim();
        String cvv = Objects.requireNonNull(binding.textInputLayoutCardCVV.getEditText()).getText().toString().trim();

        Name name = Name.fromFullName(Objects.requireNonNull(binding.textInputLayoutUsername.getEditText()).getText().toString());

        CreditCardDetails details = new CreditCardDetails(name, expiryDate, creditCardNumber, cvv);
        SecureElement card = getElement() == null ?
                new SecureElement(title, details) :
                getElement();

        card.setTags(binding.tagView.getTags());
        card.setTitle(title);
        card.setDetail(details);

        return card;
    }

    @Override
    public ElementType getSecureElementType() {
        return ElementType.CREDIT_CARD;
    }

    private void insertCard(EmvCard card){
        Name name = new Name(card.getHolderFirstname(), card.getHolderLastname());
        String cardNumber = card.getCardNumber();
        String expireString = CreditCardUtil.formatDate(card.getExpireDate());

        Objects.requireNonNull(binding.textInputLayoutUsername.getEditText()).setText(name.getFullName());
        Objects.requireNonNull(binding.textInputLayoutCardNumber.getEditText()).setText(cardNumber);
        Objects.requireNonNull(binding.textInputLayoutCardDate.getEditText()).setText(expireString);
    }

    private void setNfcMessageSuccess(@StringRes int stringRes){
        setNfcMessage(stringRes, colorGreen);
    }

    private void setNfcMessageError(@StringRes int stringRes){
        setNfcMessage(stringRes, colorRed);
    }

    private void setNfcMessage(@StringRes int stringRes, @ColorInt int colorInt){
        binding.nfc.setInformationText(stringRes);
        binding.nfc.setInformationTextColor(colorInt);
        DrawableCompat.setTint(binding.nfc.getIconDrawable(), colorInt);
        startResetNfcMessage();
    }

    private void startResetNfcMessage(){
        if(timer != null)
            timer.cancel();

        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    binding.nfc.setInformationText(R.string.nfc_listening);
                    binding.nfc.setInformationTextColor(dColor);
                    binding.nfc.getIconDrawable().setTint(dColor);
                });
            }
        }, 2500);
    }
}

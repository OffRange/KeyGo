package de.davis.passwordmanager.security.element.creditcard;

import java.io.Serializable;
import java.util.Base64;

import de.davis.passwordmanager.security.Cryptography;
import de.davis.passwordmanager.security.element.ElementDetail;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.utils.CreditCardUtil;

public class CreditCardDetails implements ElementDetail {

    private static final long serialVersionUID = 2239944147392047056L;

    private final Name cardholder;
    private String expirationDate;
    private final String cardNumber;
    private final String cvv;

    public CreditCardDetails(Name cardholder, String expirationDate, String cardNumber, String cvv) {
        if(CreditCardUtil.isValidDateFormat(expirationDate))
            this.expirationDate = expirationDate;

        this.cardholder = cardholder;
        this.cardNumber = cardNumber.replaceAll("\\s", "");
        this.cvv = cvv;
    }

    public Name getCardholder() {
        return cardholder;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getSecretNumber(){
        return getFormattedNumber().replaceAll("(\\d{4}\\s){3}", "**** **** **** ");
    }

    public String getFormattedNumber(){
        return CreditCardUtil.formatNumber(getCardNumber());
    }

    public String getCvv() {
        return cvv;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    @SecureElement.ElementType
    @Override
    public int getType() {
        return SecureElement.TYPE_CREDIT_CARD;
    }
}

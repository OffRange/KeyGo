package de.davis.passwordmanager.security.element.creditcard;

import de.davis.passwordmanager.security.element.ElementDetail;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.utils.CreditCardUtil;

public class CreditCardDetails implements ElementDetail {

    private static final long serialVersionUID = -6717857895639765586L;

    private Name cardholder;
    private String expirationDate;
    private String cardNumber;
    private String cvv;

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

    public void setCardholder(Name cardholder) {
        this.cardholder = cardholder;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
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

    public void setCvv(String cvv) {
        this.cvv = cvv;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    @SecureElement.ElementType
    @Override
    public int getType() {
        return SecureElement.TYPE_CREDIT_CARD;
    }
}

package de.davis.passwordmanager.database.entities.details.creditcard;

import java.io.Serial;
import java.util.Objects;

import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.database.entities.details.ElementDetail;
import de.davis.passwordmanager.utils.CreditCardUtil;

public class CreditCardDetails implements ElementDetail {

    @Serial
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

    @Override
    public ElementType getElementType() {
        return ElementType.CREDIT_CARD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CreditCardDetails that = (CreditCardDetails) o;

        if (!Objects.equals(cardholder, that.cardholder))
            return false;
        if (!Objects.equals(expirationDate, that.expirationDate))
            return false;
        if (!cardNumber.equals(that.cardNumber)) return false;
        return Objects.equals(cvv, that.cvv);
    }

    @Override
    public int hashCode() {
        int result = cardholder != null ? cardholder.hashCode() : 0;
        result = 31 * result + (expirationDate != null ? expirationDate.hashCode() : 0);
        result = 31 * result + cardNumber.hashCode();
        result = 31 * result + (cvv != null ? cvv.hashCode() : 0);
        return result;
    }
}

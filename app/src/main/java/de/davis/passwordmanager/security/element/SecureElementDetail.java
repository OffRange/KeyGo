package de.davis.passwordmanager.security.element;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import java.util.LinkedHashMap;
import java.util.Map;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.element.creditcard.CreditCardDetails;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.ui.elements.CreateSecureElementActivity;
import de.davis.passwordmanager.ui.elements.creditcard.CreateCreditCardActivity;
import de.davis.passwordmanager.ui.elements.creditcard.ViewCreditCardFragment;
import de.davis.passwordmanager.ui.elements.password.CreatePasswordActivity;
import de.davis.passwordmanager.ui.elements.password.ViewPasswordFragment;

public class SecureElementDetail {

    private final Class<? extends CreateSecureElementActivity> createClass;
    private final Class<? extends ElementDetail> elementDetailClass;
    @IdRes private final int viewFragmentId;
    private final int title;
    private final int icon;

    public SecureElementDetail(Class<? extends CreateSecureElementActivity> createClass, Class<? extends ElementDetail> elementDetailClass, @IdRes int viewFragmentId, @DrawableRes int icon, @StringRes int title) {
        this.createClass = createClass;
        this.elementDetailClass = elementDetailClass;
        this.viewFragmentId = viewFragmentId;
        this.icon = icon;
        this.title = title;
    }

    public int getTitle() {
        return title;
    }

    public int getIcon() {
        return icon;
    }

    public Class<? extends CreateSecureElementActivity> getCreateActivityClass(){
        return createClass;
    }

    public Class<? extends ElementDetail> getElementDetailClass() {
        return elementDetailClass;
    }

    public int getViewFragmentId() {
        return viewFragmentId;
    }

    public static Map<Integer, SecureElementDetail> getRegisteredDetails(){
        LinkedHashMap<Integer, SecureElementDetail> map = new LinkedHashMap<>();

        map.put(SecureElement.TYPE_PASSWORD, new SecureElementDetail(CreatePasswordActivity.class, PasswordDetails.class, ViewPasswordFragment.ID, R.drawable.ic_baseline_password_24, R.string.password));
        map.put(SecureElement.TYPE_CREDIT_CARD, new SecureElementDetail(CreateCreditCardActivity.class, CreditCardDetails.class, ViewCreditCardFragment.ID, R.drawable.ic_baseline_credit_card_24, R.string.credit_card));

        return map;
    }

    public static SecureElementDetail getFor(SecureElement element) {
        return getFor(element.getType());
    }

    public static SecureElementDetail getFor(@SecureElement.ElementType int type) {
        return getRegisteredDetails().get(type);
    }
}


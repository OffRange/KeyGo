package de.davis.passwordmanager.security.element.password;

import de.davis.passwordmanager.security.Cryptography;
import de.davis.passwordmanager.security.element.ElementDetail;
import de.davis.passwordmanager.security.element.SecureElement;

public class PasswordDetails implements ElementDetail {

    private static final long serialVersionUID = 4938873580704485021L;

    private byte[] password;
    private String origin;
    private String username;
    private Strength strength;

    public PasswordDetails(String password, String origin, String username) {
        setPassword(password);
        this.origin = origin;
        this.username = username;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Strength getStrength() {
        return strength;
    }

    public void setPassword(String password){
        this.password = Cryptography.encryptAES(password.getBytes());
        this.strength = Strength.estimateStrength(password);
    }

    public byte[] getPasswordData() {
        return password;
    }

    public String getPassword(){
        return new String(Cryptography.decryptAES(getPasswordData()));
    }

    @SecureElement.ElementType
    @Override
    public int getType() {
        return SecureElement.TYPE_PASSWORD;
    }
}

package de.davis.passwordmanager.database.entities.details.password;

import java.io.Serial;
import java.util.Objects;

import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.database.entities.details.ElementDetail;
import de.davis.passwordmanager.security.Cryptography;

public class PasswordDetails implements ElementDetail {

    @Serial
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

    @Override
    public ElementType getElementType() {
        return ElementType.PASSWORD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordDetails that = (PasswordDetails) o;
        return getPassword().equals(that.getPassword()) && Objects.equals(origin, that.origin) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, username, getPassword());
    }
}

package de.davis.passwordmanager.database.entities.details.creditcard;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class Name implements Serializable {

    @Serial
    private static final long serialVersionUID = 7569988556632962328L;

    private String lastName;
    private String firstName;

    public Name(String lastName, String firstName) {
        this.lastName = lastName;
        this.firstName = firstName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName(){
        if(getFirstName() == null || getLastName() == null)
            return null;

        return getFirstName() +" "+ getLastName();
    }

    public static Name fromFullName(String fullName){
        return new Name(fullName.contains(" ") ? fullName.substring(0, fullName.lastIndexOf(" ")): "",
                fullName.contains(" ") ? fullName.substring(fullName.lastIndexOf(" ")+1) : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Name name)) return false;
        return Objects.equals(lastName, name.lastName) && Objects.equals(firstName, name.firstName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastName, firstName);
    }
}

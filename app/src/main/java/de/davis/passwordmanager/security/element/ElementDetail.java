package de.davis.passwordmanager.security.element;

import java.io.Serializable;

public interface ElementDetail extends Serializable {

    @SecureElement.ElementType
    int getType();
}

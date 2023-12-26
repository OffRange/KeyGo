package de.davis.passwordmanager.database.entities.details;

import java.io.Serializable;

import de.davis.passwordmanager.database.ElementType;

public interface ElementDetail extends Serializable {

    ElementType getElementType();
}

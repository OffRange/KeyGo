package de.davis.passwordmanager.filter;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails;
import de.davis.passwordmanager.database.entities.details.password.Strength;

public class Filter {

    public static final Filter DEFAULT = new Filter();

    private static final int ID_PASSWORD = R.id.password;
    private static final int ID_CREDIT_CARD = R.id.creditCard;

    private static final int ID_VERY_STRONG = R.id.veryStrong;
    private static final int ID_STRONG = R.id.strong;
    private static final int ID_MODERATE = R.id.moderate;
    private static final int ID_WEAK = R.id.weak;
    private static final int ID_RIDICULOUS = R.id.ridiculous;

    private ChipGroup type;
    private ChipGroup strength;

    private final List<Integer> selectedIds = new ArrayList<>();

    private Filter(){}

    public void setType(ChipGroup type) {
        this.type = type;
    }

    public void setStrength(ChipGroup strength) {
        this.strength = strength;
    }

    public Runnable updater;

    public void setUpdater(Runnable updater) {
        this.updater = updater;
    }

    public void update() {
        if(updater != null)
            updater.run();

        if(!groupsSet())
            return;

        selectedIds.clear();
        selectedIds.addAll(type.getCheckedChipIds());
        selectedIds.addAll(strength.getCheckedChipIds());
    }

    public List<Integer> getSelectedIds() {
        return selectedIds;
    }

    private boolean groupsSet(){
        return type != null && strength != null;
    }

    public List<SecureElement> filter(List<SecureElement> elements) {
        if(!groupsSet())
            return elements;

        List<SecureElement> toFilter = new ArrayList<>(elements);
        List<Integer> typeIds = type.getCheckedChipIds();
        List<Integer> strengthIds = strength.getCheckedChipIds();
        if(!typeIds.contains(ID_CREDIT_CARD))
            toFilter.removeIf(element -> element.getElementType() == ElementType.CREDIT_CARD);


        if(!typeIds.contains(ID_PASSWORD)) {
            toFilter.removeIf(element -> element.getElementType() == ElementType.PASSWORD);
            return toFilter;
        }

        toFilter.removeIf(element -> {
            if(element.getElementType() != ElementType.PASSWORD)
                return false;

            return !strengthIds.contains(ID_VERY_STRONG) && ((PasswordDetails)element.getDetail()).getStrength() == Strength.VERY_STRONG
                    || !strengthIds.contains(ID_STRONG) && ((PasswordDetails)element.getDetail()).getStrength() == Strength.STRONG
                    || !strengthIds.contains(ID_MODERATE) && ((PasswordDetails)element.getDetail()).getStrength() == Strength.MODERATE
                    || !strengthIds.contains(ID_WEAK) && ((PasswordDetails)element.getDetail()).getStrength() == Strength.WEAK
                    || !strengthIds.contains(ID_RIDICULOUS) && ((PasswordDetails)element.getDetail()).getStrength() == Strength.RIDICULOUS;
        });

        return toFilter;
    }
}

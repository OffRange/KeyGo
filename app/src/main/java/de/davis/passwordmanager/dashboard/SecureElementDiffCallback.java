package de.davis.passwordmanager.dashboard;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import de.davis.passwordmanager.security.element.SecureElement;

public class SecureElementDiffCallback extends DiffUtil.Callback {

    private final List<Item> oldItems;
    private final List<Item> newItems;

    public SecureElementDiffCallback(List<Item> oldItems, List<Item> newItems) {
        this.oldItems = oldItems;
        this.newItems = newItems;
    }

    @Override
    public int getOldListSize() {
        return oldItems.size();
    }

    @Override
    public int getNewListSize() {
        return newItems.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldItems.get(oldItemPosition).getId() == newItems.get(newItemPosition).getId();
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        Item oldItem = oldItems.get(oldItemPosition);
        Item newItem = newItems.get(newItemPosition);

        if(oldItem instanceof SecureElement && newItem instanceof SecureElement){
            return ((SecureElement) oldItem).getUniqueString().equals(((SecureElement) newItem).getUniqueString());
        }

        return false;
    }
}

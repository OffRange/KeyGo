package de.davis.passwordmanager.dashboard;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

public class SelectionStateChangedDiffCallback extends DiffUtil.Callback {

    private final int listSize;
    private final boolean selectable;

    public SelectionStateChangedDiffCallback(int listSize, boolean selectable) {
        this.listSize = listSize;
        this.selectable = selectable;
    }

    @Override
    public int getOldListSize() {
        return listSize;
    }

    @Override
    public int getNewListSize() {
        return getOldListSize();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return true;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return false;
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        return selectable;
    }
}

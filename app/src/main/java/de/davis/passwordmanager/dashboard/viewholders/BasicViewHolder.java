package de.davis.passwordmanager.dashboard.viewholders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.davis.passwordmanager.dashboard.Item;
import de.davis.passwordmanager.dashboard.selection.SecureElementDetailsLookup;

public abstract class BasicViewHolder<T extends Item> extends RecyclerView.ViewHolder implements SecureElementDetailsLookup.ItemDetailsLookup {

    public BasicViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    public void bind(@NonNull T item, String filter, OnItemClickedListener<T> onItemClickedListener, boolean selected){
        bindGeneral(item, filter, onItemClickedListener);
        handleSelectionState(selected);
    }

    protected abstract void bindGeneral(@NonNull T item, String filter, OnItemClickedListener<T> onItemClickedListener);

    protected abstract void handleSelectionState(boolean selected);

    public interface OnItemClickedListener<T> {
        void onClicked(T element);
    }
}
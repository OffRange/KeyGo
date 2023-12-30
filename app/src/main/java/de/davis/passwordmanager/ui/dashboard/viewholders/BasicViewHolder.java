package de.davis.passwordmanager.ui.dashboard.viewholders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

import de.davis.passwordmanager.database.dtos.Item;
import de.davis.passwordmanager.ui.dashboard.selection.SecureElementDetailsLookup;

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

    @Override
    public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
        return new ItemDetailsLookup.ItemDetails<>() {
            @Override
            public int getPosition() {
                return getAbsoluteAdapterPosition();
            }

            @Override
            public Long getSelectionKey() {
                return getItemId();
            }
        };
    }

    public interface OnItemClickedListener<T> {
        void onClicked(T element);
    }
}
package de.davis.passwordmanager.ui.dashboard.selection;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.widget.RecyclerView;

public class SecureElementDetailsLookup extends ItemDetailsLookup<Long> {

    private final RecyclerView recyclerView;

    public SecureElementDetailsLookup(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    @Nullable
    @Override
    public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
        View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if(view == null)
            return null;

        RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(view);
        if(!(holder instanceof ItemDetailsLookup))
            return null;

        return ((ItemDetailsLookup) holder).getItemDetails();
    }

    public interface ItemDetailsLookup {
        ItemDetails<Long> getItemDetails();
    }
}

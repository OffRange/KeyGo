package de.davis.passwordmanager.dashboard.viewholders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.davis.passwordmanager.dashboard.selection.SecureElementDetailsLookup;
import de.davis.passwordmanager.database.dto.SecureElement;

public abstract class BasicViewHolder<T> extends RecyclerView.ViewHolder implements SecureElementDetailsLookup.ItemDetailsLookup {

    public BasicViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    public abstract void bind(@NonNull T item, String filter, OnItemClickedListener onItemClickedListener);

    public abstract void onBindSelectablePayload(boolean selectable, boolean selected);

    public interface OnItemClickedListener {
        void onClicked(SecureElement element);
    }
}
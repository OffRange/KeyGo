package de.davis.passwordmanager.dashboard.viewholders;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.davis.passwordmanager.dashboard.selection.SecureElementDetailsLookup;

public abstract class BasicViewHolder<T> extends RecyclerView.ViewHolder implements SecureElementDetailsLookup.ItemDetailsLookup {

    public BasicViewHolder(@NonNull View itemView) {
        super(itemView);
    }

    public abstract void bind(@NonNull T item, String filter);

    public abstract void onBindSelectablePayload(boolean selectable, boolean selected);
}
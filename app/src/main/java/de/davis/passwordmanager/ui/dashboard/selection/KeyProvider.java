package de.davis.passwordmanager.ui.dashboard.selection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

public class KeyProvider extends ItemKeyProvider<Long> {

    private final RecyclerView recyclerView;

    public KeyProvider(RecyclerView recyclerView) {
        super(SCOPE_MAPPED);
        this.recyclerView = recyclerView;
    }

    @Nullable
    @Override
    public Long getKey(int position) {
        return Objects.requireNonNull(recyclerView.getAdapter()).getItemId(position);
    }

    @Override
    public int getPosition(@NonNull Long key) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForItemId(key);
        return holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
    }
}

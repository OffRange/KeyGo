package de.davis.passwordmanager.dashboard.viewholders;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.ItemDetailsLookup;

import com.google.android.material.checkbox.MaterialCheckBox;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dashboard.Header;

public class HeaderViewHolder extends BasicViewHolder<Header> {

    private final TextView header;
    private final MaterialCheckBox checkBox;

    public HeaderViewHolder(@NonNull View itemView) {
        super(itemView);
        this.header = itemView.findViewById(R.id.title);
        this.checkBox = itemView.findViewById(R.id.checkBox);
    }

    @Override
    public void bind(@NonNull Header item) {
        header.setText(item.getHeader());
    }

    @Override
    public void onBindSelectablePayload(boolean selectable, boolean selected) {
        checkBox.setVisibility(selectable ? View.VISIBLE : View.GONE);
        checkBox.setChecked(selected);
    }

    public void onChildSelected(boolean selectable, @MaterialCheckBox.CheckedState int selected){
        checkBox.setVisibility(selectable ? View.VISIBLE : View.GONE);
        checkBox.setCheckedState(selected);
    }

    @Override
    public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
        return new ItemDetailsLookup.ItemDetails<Long>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @Override
            public Long getSelectionKey() {
                return getItemId();
            }
        };
    }
}

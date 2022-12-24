package de.davis.passwordmanager.dashboard.viewholders;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.ItemDetailsLookup;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementDetail;
import de.davis.passwordmanager.security.element.creditcard.CreditCardDetails;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.ui.views.OptionBottomSheet;

public class SecureElementViewHolder extends BasicViewHolder<SecureElement> {

    private final ImageButton more;
    private final ImageView image, typeIcon;
    private final TextView title, info, type;
    private final MaterialCheckBox checkBox;

    public SecureElementViewHolder(@NonNull View itemView) {
        super(itemView);
        more = itemView.findViewById(R.id.more);
        image = itemView.findViewById(R.id.image);
        title = itemView.findViewById(R.id.title);
        info = itemView.findViewById(R.id.info);
        type = itemView.findViewById(R.id.type);
        typeIcon = itemView.findViewById(R.id.typeIcon);
        checkBox = itemView.findViewById(R.id.checkboxSelection);
    }

    @Override
    public void bind(@NonNull SecureElement item) {
        Context context = itemView.getContext();

        title.setText(item.getTitle());
        type.setText(item.getTypeName());

        typeIcon.setImageResource(SecureElementDetail.getFor(item).getIcon());
        image.setImageDrawable(item.getIcon(context));

        if(item.getType() == SecureElement.TYPE_PASSWORD){
            info.setText(((PasswordDetails)item.getDetail()).getStrength().getString());
            info.setTextColor(((PasswordDetails)item.getDetail()).getStrength().getColor(context));
        }else{
            CreditCardDetails details = (CreditCardDetails) item.getDetail();
            info.setText(details.getSecretNumber());
            info.setTextColor(MaterialColors.getColor(itemView.getContext(), com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        }

        itemView.setOnClickListener(v -> context.startActivity(new Intent(itemView.getContext(), SecureElementDetail.getFor(item).getViewActivityClass()).putExtra(Keys.KEY_OLD, item)));

        more.setOnClickListener(v -> new OptionBottomSheet(itemView.getContext(), item).show());
    }

    @Override
    public void onBindSelectablePayload(boolean selectable, boolean selected) {
        more.setVisibility(selectable ? View.GONE : View.VISIBLE);
        checkBox.setVisibility(selectable ? View.VISIBLE : View.GONE);
        checkBox.setChecked(selected);
    }

    @Override
    public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
        return new ItemDetailsLookup.ItemDetails<>() {
            @Override
            public int getPosition() {
                return getAdapterPosition();
            }

            @NonNull
            @Override
            public Long getSelectionKey() {
                return getItemId();
            }
        };
    }
}

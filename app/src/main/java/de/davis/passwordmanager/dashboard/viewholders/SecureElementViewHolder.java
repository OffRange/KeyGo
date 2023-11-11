package de.davis.passwordmanager.dashboard.viewholders;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.ItemDetailsLookup;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;

import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.database.dto.SecureElement;
import de.davis.passwordmanager.database.entities.details.creditcard.CreditCardDetails;
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails;
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
    public void bind(@NonNull SecureElement item, String filter, OnItemClickedListener onItemClickedListener) {
        Context context = itemView.getContext();

        String text = item.getTitle();
        Spannable spannable = new SpannableString(text);
        if(filter != null && !filter.isEmpty()){
            int index = -1;
            while ((index = text.toLowerCase().indexOf(filter.toLowerCase(), index + 1)) != -1) {
                int endIndex = index + filter.length();
                spannable.setSpan(new BackgroundColorSpan(MaterialColors.getColor(title, com.google.android.material.R.attr.colorPrimary)), index, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new ForegroundColorSpan(MaterialColors.getColor(title, com.google.android.material.R.attr.colorOnPrimary)), index, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        title.setText(spannable);

        type.setText(item.getElementType().getTitle());

        typeIcon.setImageResource(item.getElementType().getIcon());
        image.setImageDrawable(item.getIcon(context));

        if(item.getElementType() == ElementType.PASSWORD){
            info.setText(((PasswordDetails)item.getDetail()).getStrength().getString());
            info.setTextColor(((PasswordDetails)item.getDetail()).getStrength().getColor(context));
        }else{
            CreditCardDetails details = (CreditCardDetails) item.getDetail();
            info.setText(details.getSecretNumber());
            info.setTextColor(MaterialColors.getColor(itemView.getContext(), com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        }

        itemView.setOnClickListener(v -> {
            if(onItemClickedListener != null)
                onItemClickedListener.onClicked(item);
        });

        more.setOnClickListener(v -> new OptionBottomSheet(itemView.getContext(), List.of(item)).show());
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

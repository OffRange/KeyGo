package de.davis.passwordmanager.ui.dashboard.viewholders;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.database.entities.details.creditcard.CreditCardDetails;
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails;
import de.davis.passwordmanager.ui.views.OptionBottomSheet;

public class SecureElementViewHolder extends BasicViewHolder<SecureElement> {

    private final ImageButton more;
    private final ImageView image, typeIcon;
    private final TextView title, info, type;
    public final TextView letterView;

    private final FragmentManager fragmentManager;

    public SecureElementViewHolder(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @NonNull FragmentManager fragmentManager) {
        super(inflater.inflate(R.layout.layout_element, parent, false));
        more = itemView.findViewById(R.id.more);
        image = itemView.findViewById(R.id.image);
        title = itemView.findViewById(R.id.title);
        info = itemView.findViewById(R.id.info);
        type = itemView.findViewById(R.id.type);
        letterView = itemView.findViewById(R.id.header);
        typeIcon = itemView.findViewById(R.id.typeIcon);
        this.fragmentManager = fragmentManager;
    }

    @Override
    public void bindGeneral(@NonNull SecureElement item, String filter, OnItemClickedListener<SecureElement> onItemClickedListener) {
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
            setShortenedTextIfNeeded(info, details.getSecretNumber(), details.getSecretNumber().substring(15, 19));
            info.setTextColor(MaterialColors.getColor(itemView.getContext(), com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        }

        itemView.setOnClickListener(v -> {
            if(onItemClickedListener != null)
                onItemClickedListener.onClicked(item);
        });

        more.setOnClickListener(v -> new OptionBottomSheet<>(List.of(item), SecureElement.class).show(fragmentManager, "OptionSheet"));
    }

    private void setShortenedTextIfNeeded(TextView textView, String originalText, String shortenedText) {
        textView.post(() -> {
            int availableWidth = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
            textView.setText(originalText);

            if (textView.getPaint().measureText(originalText) > availableWidth)
                textView.setText(shortenedText);
        });
    }

    public void setLetterVisible(boolean visible, char letter){
        this.letterView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        this.letterView.setText(visible ? Character.toString(letter) : "");
    }

    @SuppressLint("PrivateResource")
    @Override
    protected void handleSelectionState(boolean selected) {
        ((MaterialCardView) itemView).setChecked(selected);
        ((MaterialCardView) itemView).setStrokeWidth(selected ? (int) itemView.getResources().getDimension(com.google.android.material.R.dimen.m3_comp_outlined_card_outline_width) : 0);
    }
}

package de.davis.passwordmanager.ui.views;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;

public class UpdaterPreference extends Preference {

    private PreferenceViewHolder holder;

    public UpdaterPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public UpdaterPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public UpdaterPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public UpdaterPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        this.holder = holder;
    }

    public void setHighlighted(boolean highlighted){
        holder.itemView.post(() -> holder.itemView.setBackgroundColor(MaterialColors.getColor(getContext(), highlighted ? com.google.android.material.R.attr.colorSecondaryContainer : com.google.android.material.R.attr.colorSurface, Color.RED)));
    }
}

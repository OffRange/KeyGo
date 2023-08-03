package de.davis.passwordmanager.ui.views;

import android.content.Context;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.customview.view.AbsSavedState;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;

public class VersionPreference extends Preference {

    private View view;
    private boolean highlighted;

    public VersionPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public VersionPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public VersionPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public VersionPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        this.view = holder.itemView;
        setBackground();

    }

    public void setHighlighted(boolean highlighted){
        this.highlighted = highlighted;
        setBackground();
    }

    private void setBackground(){
        if(view == null)
            return;

        view.setBackgroundColor(MaterialColors.getColor(getContext(), highlighted ? com.google.android.material.R.attr.colorSecondaryContainer : com.google.android.material.R.attr.colorSurface, Color.RED));
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState ss = new SavedState(super.onSaveInstanceState());
        ss.highlighted = highlighted;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(@Nullable Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        highlighted = ss.highlighted;
    }

    static class SavedState extends AbsSavedState {
        boolean highlighted;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(@NonNull Parcel source, ClassLoader loader) {
            super(source, loader);
            highlighted = source.readInt() == 1;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(highlighted ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR =
                new ClassLoaderCreator<>() {
                    @NonNull
                    @Override
                    public SavedState createFromParcel(@NonNull Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Nullable
                    @Override
                    public SavedState createFromParcel(@NonNull Parcel in) {
                        return new SavedState(in, null);
                    }

                    @NonNull
                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}

package de.davis.passwordmanager.ui.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageButton;

import com.google.android.material.color.MaterialColors;

import de.davis.passwordmanager.R;

public class CheckableImageButton extends AppCompatImageButton implements Checkable {

    private boolean checked;

    public CheckableImageButton(@NonNull Context context) {
        super(context);
        init();
    }

    public CheckableImageButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CheckableImageButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        Drawable drawable = AppCompatResources.getDrawable(getContext(), R.drawable.password_eye);
        if(drawable == null)
            return;

        drawable.setTint(MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, Color.BLACK));
        setImageDrawable(drawable);
    }

    @Override
    public void setChecked(boolean checked) {
        if(this.checked == checked)
            return;

        this.checked = checked;
        setImageState(new int[]{android.R.attr.state_checked * (checked ? 1 : -1)}, true);
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked());
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }
}

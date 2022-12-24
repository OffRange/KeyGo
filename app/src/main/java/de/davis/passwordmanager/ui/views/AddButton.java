package de.davis.passwordmanager.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import de.davis.passwordmanager.utils.ResUtil;

public class AddButton extends AppCompatTextView {

    public AddButton(@NonNull Context context) {
        super(context);
        init();
    }

    public AddButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AddButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        setBackgroundResource(ResUtil.resolveAttribute(getContext().getTheme(), androidx.appcompat.R.attr.selectableItemBackground));

        int dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getContext().getResources().getDisplayMetrics());

        setClickable(true);
        setFocusable(true);
        setPadding(dp, dp, dp, dp);
        setHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 65, getContext().getResources().getDisplayMetrics()));

        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        setGravity(Gravity.CENTER_VERTICAL);

        setCompoundDrawablePadding(dp/2);
    }

    public void setIcon(int id){
        setCompoundDrawablesWithIntrinsicBounds(id, 0, 0, 0);
    }
}

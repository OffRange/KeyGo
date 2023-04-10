package de.davis.passwordmanager.ui;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;

public class LinearLayoutManager extends androidx.recyclerview.widget.LinearLayoutManager {

    private final Context context;

    public LinearLayoutManager(Context context) {
        super(context);
        this.context = context;
    }

    public LinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
        this.context = context;
    }

    public LinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
    }

    @Override
    public boolean isAutoMeasureEnabled() {
        return false;
    }

    @Override
    public int getPaddingBottom() {
        float dip = 56+16*2;
        Resources r = context.getResources();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, r.getDisplayMetrics());
        return (int) px;
    }
}

package de.davis.passwordmanager.ui;

import android.content.Context;
import android.util.AttributeSet;

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
        return LayoutManagerKt.getPaddingBottom(context);
    }
}

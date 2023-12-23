package de.davis.passwordmanager.ui

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager

class GridLayoutManager(
    val context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : GridLayoutManager(context, attributeSet, defStyleAttr, defStyleRes) {

    init {
        spanCount = 2
    }

    override fun getPaddingBottom(): Int {
        return getPaddingBottom(context)
    }
}
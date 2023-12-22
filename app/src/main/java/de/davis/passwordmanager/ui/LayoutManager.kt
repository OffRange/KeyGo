package de.davis.passwordmanager.ui

import android.content.Context
import android.util.TypedValue

internal fun getPaddingBottom(context: Context): Int {
    val dip = (56 + 16 * 2).toFloat()

    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dip,
        context.resources.displayMetrics
    ).toInt()
}
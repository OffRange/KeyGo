package de.davis.passwordmanager

import android.app.Activity
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty

fun Activity.fix() {
    val decor = window.decorView
    if (decor.getTag(R.id.tag_edge_to_edge_installed) == true) return
    decor.setTag(R.id.tag_edge_to_edge_installed, true)

    WindowCompat.enableEdgeToEdge(window)

    val content = findViewById<ViewGroup>(android.R.id.content) ?: return

    val initL = content.paddingLeft
    val initT = content.paddingTop
    val initR = content.paddingRight
    val initB = content.paddingBottom

    val extraTopForActionBar = (this as? AppCompatActivity)?.let { abHost ->
        val ab = abHost.supportActionBar
        if (ab != null && ab.isShowing) actionBarHeight() else 0
    } ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )

        Log.d("EdgeToEdgeFix", "$initT ${bars.top} $extraTopForActionBar")
        v.setPadding(
            initL + bars.left,
            initT + bars.top,
            initR + bars.right,
            initB + bars.bottom
        )

        insets
    }

    // If no child is attached yet, wait for the first one so we can request insets;
    // otherwise request immediately.
    if (content.isEmpty()) {
        content.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                parent.viewTreeObserver.addOnPreDrawListener(object :
                    ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        parent.viewTreeObserver.removeOnPreDrawListener(this)
                        ViewCompat.requestApplyInsets(parent)
                        return true
                    }
                })
                content.setOnHierarchyChangeListener(null)
            }

            override fun onChildViewRemoved(parent: View, child: View) {}
        })
    } else {
        ViewCompat.requestApplyInsets(content)
    }
}

private fun Activity.actionBarHeight(): Int {
    val tv = TypedValue()
    return if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
        TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
    } else 0
}
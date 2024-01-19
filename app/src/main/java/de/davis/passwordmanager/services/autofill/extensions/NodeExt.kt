@file:RequiresApi(Build.VERSION_CODES.O)

package de.davis.passwordmanager.services.autofill.extensions

import android.app.assist.AssistStructure.ViewNode
import android.app.assist.AssistStructure.WindowNode
import android.os.Build
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi

fun WindowNode.getPackageName() = title.split("/").first()

fun ViewNode.findChildById(id: AutofillId): ViewNode? {
    if (autofillId == id) return this
    for (i in 0 until childCount) {
        val child = getChildAt(i).findChildById(id)
        if (child != null) return child
    }
    return null
}
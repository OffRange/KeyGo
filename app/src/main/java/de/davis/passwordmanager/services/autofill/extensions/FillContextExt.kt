@file:RequiresApi(Build.VERSION_CODES.O)

package de.davis.passwordmanager.services.autofill.extensions

import android.app.assist.AssistStructure
import android.os.Build
import android.service.autofill.FillContext
import androidx.annotation.RequiresApi

fun Collection<FillContext>.getWindowNodes(): List<AssistStructure.WindowNode> {
    val fillContext = lastOrNull() ?: return emptyList()
    return (0 until fillContext.structure.windowNodeCount).map {
        fillContext.structure.getWindowNodeAt(it)
    }
}

fun FillContext.getWindowNodes(): List<AssistStructure.WindowNode> = listOf(this).getWindowNodes()
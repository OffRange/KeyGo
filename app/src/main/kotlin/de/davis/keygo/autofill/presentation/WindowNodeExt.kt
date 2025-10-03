package de.davis.keygo.autofill.presentation

import android.app.assist.AssistStructure

internal val AssistStructure.WindowNode.packageName get() = title.split("/").first()